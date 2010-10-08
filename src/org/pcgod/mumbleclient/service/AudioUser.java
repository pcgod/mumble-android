package org.pcgod.mumbleclient.service;

import java.util.Arrays;
import java.util.LinkedList;

import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

class AudioUser {
	//JitterBuffer jb;
	User u;
	short[] pfBuffer;
	private int bufferFilled;
	private boolean lastAlive = true;
	private final LinkedList<byte[]> frameList = new LinkedList<byte[]>();
	private int missCount;
	private int ucFlags = 0xFF;
	private boolean hasTerminator;
	private final short[] pOut = new short[MumbleConnection.FRAME_SIZE];
	private final AudioTrack at;
	private final short[] out;
	private final int bufferSize = MumbleConnection.FRAME_SIZE;
	private static double[] fadeIn;
	private static double[] fadeOut;
	long jitterBuffer;
	Object jbLock = new Object();

	static {
		fadeIn = new double[MumbleConnection.FRAME_SIZE];
		fadeOut = new double[MumbleConnection.FRAME_SIZE];
		final double mul = Math.PI / (2.0 * MumbleConnection.FRAME_SIZE);
		for (int i = 0; i < MumbleConnection.FRAME_SIZE; ++i) {
			fadeIn[i] = fadeOut[MumbleConnection.FRAME_SIZE - i - 1] = Math
					.sin(i * mul);
		}
	}

	AudioUser(final User u_) {
		u = u_;
//		jb = new JitterBuffer(MumbleConnection.FRAME_SIZE);
//		jb.setMargin(50 * MumbleConnection.FRAME_SIZE);

		jitterBuffer = Native.jitter_buffer_init(MumbleConnection.FRAME_SIZE);
		// 0 = JITTER_BUFFER_SET_MARGIN
		Native.jitter_buffer_ctl(jitterBuffer, 0,
				new int[] { 50 * MumbleConnection.FRAME_SIZE });

		at = new AudioTrack(AudioManager.STREAM_MUSIC,
				MumbleConnection.SAMPLE_RATE,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, bufferSize * 20,
				AudioTrack.MODE_STREAM);

		out = new short[bufferSize];
		at.play();
		Log.i("mc", "Create audio user for " + u_.name);
	}

	@Override
	protected final void finalize() {
		Native.jitter_buffer_destroy(jitterBuffer);
	}

	void addFrameToBuffer(final PacketDataStream pds, final int flags) {
		// skip iSeq
		pds.readLong();

		int frames = 0;
		int header = 0;
		do {
			header = pds.next();
			++frames;
			pds.skip(header & 0x7f);
		} while (((header & 0x80) > 0) && pds.isValid());

		if (pds.isValid()) {
			pds.rewind();
			pds.skip(1);
			// skip uiSession
			pds.readLong();
			/* final long iSeq = */pds.readLong();

//			final JitterBufferPacket jbp = new JitterBufferPacket();
//			jbp.flags = flags;
//			jbp.data = packet;
//			jbp.span = MumbleConnection.FRAME_SIZE * frames;
//			jbp.timestamp = MumbleConnection.FRAME_SIZE * iSeq;

//			final Native.JitterBufferPacket njbp = new Native.JitterBufferPacket();
//			njbp.user_data = flags;
//			byte tmp[] = new byte[packet.remaining()];
//			packet.get(tmp);
//			njbp.data = tmp;
//			njbp.len = tmp.length;
//			njbp.span = MumbleConnection.FRAME_SIZE * frames;
//			njbp.timestamp = MumbleConnection.FRAME_SIZE * iSeq;

			final byte[] tmp = new byte[256];
			header = 0;
			do {
				header = pds.next();
				if (header > 0) {
					final int len = header & 0x7f;
					pds.dataBlock(tmp, len);
					Native.celt_decode(AudioOutput.celtDecoder, tmp, len, pOut);

					at.write(pOut, 0, MumbleConnection.FRAME_SIZE);
				} else {
					hasTerminator = true;
				}
			} while (((header & 0x80) > 0) && pds.isValid());

//			synchronized (jb) {
//				jb.put(jbp);
//			}

//			synchronized (jbLock) {
//				Native.jitter_buffer_put(jitterBuffer, njbp);
//			}
		}
	}

	boolean needSamples(final int snum) {
		if (pfBuffer == null) {
			pfBuffer = new short[snum];
		}

		bufferFilled = 0;
		boolean nextAlive = lastAlive;

		while (bufferFilled < snum) {
			if (!lastAlive) {
				Arrays.fill(pfBuffer, bufferFilled, bufferFilled +
						MumbleConnection.FRAME_SIZE, (short) 0);
			} else {
				final int timestamp;
				final int available;
//				synchronized (jb) {
//					timestamp = jb.getTimestamp();
//					available = jb.getAvailable();
//				}

				synchronized (jbLock) {
					timestamp = Native
							.jitter_buffer_get_pointer_timestamp(jitterBuffer);
					// 3 = JITTER_BUFFER_GET_AVAILABLE_COUNT
					final int[] tmp = new int[1];
					Native.jitter_buffer_ctl(jitterBuffer, 3, tmp);
					available = tmp[0];
				}

				if (u != null && timestamp == 0) {
					final int want = (int) u.averageAvailable;
					if (available < want) {
						++missCount;
						if (missCount < 20) {
							Arrays.fill(pfBuffer, bufferFilled, bufferFilled +
									MumbleConnection.FRAME_SIZE, (short) 0);
							bufferFilled += MumbleConnection.FRAME_SIZE;
							continue;
						}
					}
				}

				if (frameList.isEmpty()) {
//					final JitterBufferPacket jbp;
//					synchronized (jb) {
//						jbp = jb.get(MumbleConnection.FRAME_SIZE);
//					}
					final Native.JitterBufferPacket jbp = new Native.JitterBufferPacket();
					jbp.data = new byte[1024];
					jbp.len = 1024;
					synchronized (jbLock) {
						final int current_timestamp[] = new int[1];
						Native.jitter_buffer_get(jitterBuffer, jbp,
								MumbleConnection.FRAME_SIZE, current_timestamp);
						final byte[] tmp = new byte[jbp.len];
						System.arraycopy(jbp.data, 0, tmp, 0, tmp.length);
						jbp.data = tmp;
					}
					if (jbp != null) {
						final PacketDataStream pds = new PacketDataStream(
								jbp.data);

						missCount = 0;
						//ucFlags = jbp.flags;
						ucFlags = jbp.user_data;
						hasTerminator = false;

						int header = 0;
						do {
							header = pds.next();
							if (header > 0) {
								final int len = header & 0x7f;
								final byte[] tmp = new byte[len];
								pds.dataBlock(tmp, len);
								frameList.add(tmp);
							} else {
								hasTerminator = true;
							}
						} while (((header & 0x80) > 0) && pds.isValid());

//						Log.i("mumbleclient", "frames: " + frames + " valid: "
//								+ pds.isValid());

//						if (pds.left() > 0) {
//							final float x = pds.readFloat();
//							final float y = pds.readFloat();
//							final float z = pds.readFloat();
//							if (ANDROID) {
//								Log.i(LOG_TAG, "x: " + x + " y: " + y + " z: " + z);
//							} else {
//								System.out.println("x: " + x + " y: " + y + " z: " + z);
//							}
//						}

						if (u != null) {
							final float a = available;
							if (available >= u.averageAvailable) {
								u.averageAvailable = a;
							} else {
								u.averageAvailable *= 0.99;
							}
						}
					} else {
//						synchronized (jb) {
//							jb.updateDelay();
//						}
						synchronized (jbLock) {
							Native.jitter_buffer_update_delay(jitterBuffer,
									jbp, null);
						}

						++missCount;
						if (missCount > 10) {
							nextAlive = false;
						}
					}
				}

				if (!frameList.isEmpty()) {
					final byte[] frame = frameList.poll();

					//synchronized (Native.class) {
					Native.celt_decode(AudioOutput.celtDecoder, frame,
							frame.length, pOut);
					//}

					at.write(pOut, 0, MumbleConnection.FRAME_SIZE);
					//at.flush();
//					System.arraycopy(pOut, 0, pfBuffer, bufferFilled, MumbleConnection.FRAME_SIZE);
					final boolean update = true;
//	                if (p) {
//	                    float &fPowerMax = p->fPowerMax;
//	                    float &fPowerMin = p->fPowerMin;
//
//	                    float pow = 0.0f;
//	                    for (unsigned int i=0;i<iFrameSize;++i)
//	                        pow += pOut[i] * pOut[i];
//	                    pow = sqrtf(pow / static_cast<float>(iFrameSize));
//
//	                    if (pow >= fPowerMax) {
//	                        fPowerMax = pow;
//	                    } else {
//	                        if (pow <= fPowerMin) {
//	                            fPowerMin = pow;
//	                        } else {
//	                            fPowerMax = 0.99f * fPowerMax;
//	                            fPowerMin += 0.0001f * pow;
//	                        }
//	                    }
//
//	                    update = (pow < (fPowerMin + 0.01f * (fPowerMax - fPowerMin)));
//	                }
					if (frameList.isEmpty() && update) {
//						synchronized (jb) {
//							jb.updateDelay();
//						}
						synchronized (jbLock) {
							Native.jitter_buffer_update_delay(jitterBuffer,
									null, null);
						}
					}

					if (frameList.isEmpty() && hasTerminator) {
						nextAlive = false;
					}
				}

//				if (!nextAlive) {
//					for (int i = 0; i < MumbleConnection.FRAME_SIZE; ++i) {
//						pOut[i] *= fadeOut[i];
//					}
//				} else if (timestamp == 0) {
//					for (int i = 0; i < MumbleConnection.FRAME_SIZE; ++i) {
//						pOut[i] *= fadeIn[i];
//					}
//				}

//				synchronized (jb) {
//					jb.tick();
//				}
				synchronized (jbLock) {
					Native.jitter_buffer_tick(jitterBuffer);
				}
			}
			bufferFilled += MumbleConnection.FRAME_SIZE;
		}

		if (u != null) {
			if (!nextAlive) {
				ucFlags = 0xFF;
			}
			switch (ucFlags) {
			case 0:
				u.talkingState = User.TALKINGSTATE_TALKING;
				break;
			case 1:
				u.talkingState = User.TALKINGSTATE_SHOUTING;
				break;
			case 0xFF:
				u.talkingState = User.TALKINGSTATE_PASSIVE;
				break;
			default:
				u.talkingState = User.TALKINGSTATE_WHISPERING;
				break;
			}
		}

		final boolean tmp = lastAlive;
		lastAlive = nextAlive;
		return tmp;
	}
}
