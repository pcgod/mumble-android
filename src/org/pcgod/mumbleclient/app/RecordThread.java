package org.pcgod.mumbleclient.app;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import org.pcgod.mumbleclient.MumbleClient;
import org.pcgod.mumbleclient.PacketDataStream;
import org.pcgod.mumbleclient.jni.SWIGTYPE_p_CELTEncoder;
import org.pcgod.mumbleclient.jni.SWIGTYPE_p_CELTMode;
import org.pcgod.mumbleclient.jni.SWIGTYPE_p_SpeexResamplerState;
import org.pcgod.mumbleclient.jni.celt;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

public class RecordThread implements Runnable {
	private static final int AUDIO_QUALITY = 60000;
	private static int frameSize;
	private static int recordingSampleRate;
	private static final int TARGET_SAMPLE_RATE = MumbleClient.SAMPLE_RATE;
	private AudioRecord ar;
	private final short[] buffer;
	private int bufferSize;
	private final SWIGTYPE_p_CELTEncoder ce;
	private final SWIGTYPE_p_CELTMode cm;
	private final int framesPerPacket = 6;
	private final LinkedList<short[]> outputQueue = new LinkedList<short[]>();
	private final short[] resampleBuffer;
	private int seq;
	private final SWIGTYPE_p_SpeexResamplerState srs;

	public RecordThread() {
		for (final int s : new int[] { 48000, 44100, 22050, 11025, 8000 }) {
			bufferSize = AudioRecord.getMinBufferSize(s,
					AudioFormat.CHANNEL_CONFIGURATION_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			if (bufferSize > 0) {
				recordingSampleRate = s;
				break;
			}
		}

		if (bufferSize < 0) {
			throw new RuntimeException("No recording sample rate found");
		}

		Log.i("mumbleclient", "Selected recording sample rate: "
				+ recordingSampleRate);

		frameSize = recordingSampleRate / 100;

		ar = new AudioRecord(MediaRecorder.AudioSource.MIC,
				recordingSampleRate, AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, 64 * 1024);

		buffer = new short[frameSize];
		resampleBuffer = new short[(int) ((frameSize / (float) recordingSampleRate) * TARGET_SAMPLE_RATE)];
		cm = celt.celt_mode_create(MumbleClient.SAMPLE_RATE,
				MumbleClient.FRAME_SIZE);
		ce = celt.celt_encoder_create(cm, 1);
		celt.celt_encoder_ctl(ce, celt.CELT_SET_PREDICTION_REQUEST, 0);
		celt
				.celt_encoder_ctl(ce, celt.CELT_SET_VBR_RATE_REQUEST,
						AUDIO_QUALITY);

		if (recordingSampleRate != TARGET_SAMPLE_RATE) {
			srs = celt.speex_resampler_init(1, recordingSampleRate,
					TARGET_SAMPLE_RATE, 3);
		} else {
			srs = null;
		}
	}

	@Override
	public final void run() {
		boolean running = true;
		android.os.Process
				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		ar.startRecording();
		while (running && !Thread.interrupted()) {
			final int read = ar.read(buffer, 0, frameSize);

			if (read == AudioRecord.ERROR_BAD_VALUE
					|| read == AudioRecord.ERROR_INVALID_OPERATION) {
				throw new RuntimeException("" + read);
			}

			short[] out;
			if (srs != null) {
				out = resampleBuffer;
				final int[] in_len = new int[] { buffer.length };
				final int[] out_len = new int[] { out.length };
				celt.speex_resampler_process_int(srs, 0, buffer, in_len, out,
						out_len);
			} else {
				out = buffer;
			}

			final int compressedSize = Math.min(AUDIO_QUALITY / (100 * 8), 127);
			final short[] compressed = new short[compressedSize];
			celt.celt_encode(ce, out, compressed, compressedSize);
			outputQueue.add(compressed);

			if (outputQueue.size() < framesPerPacket) {
				continue;
			}

			while (!outputQueue.isEmpty()) {
				final ByteBuffer tmpBuf = ByteBuffer.allocateDirect(1024);

				int flags = 0;
				flags |= MumbleClient.UDPMessageType.UDPVoiceCELTAlpha
						.ordinal() << 5;
				tmpBuf.put((byte) flags);

				final PacketDataStream pds = new PacketDataStream(tmpBuf
						.slice());
				seq += framesPerPacket;
				pds.writeLong(seq);
				for (int i = 0; i < framesPerPacket; ++i) {
					final short[] tmp = outputQueue.poll();
					if (tmp == null) {
						break;
					}
					int head = (short) tmp.length;
					if (i < framesPerPacket - 1) {
						head |= 0x80;
					}

					pds.append(head);
					pds.append(tmp);
				}

				tmpBuf.rewind();
				final byte[] dst = new byte[pds.size() + 1];
				tmpBuf.get(dst);
				try {
					ServerList.client.sendUdpTunnelMessage(dst);
				} catch (final IOException e) {
					e.printStackTrace();
					running = false;
					break;
				}
			}
		}
		ar.stop();
	}

	@Override
	protected final void finalize() {
		celt.speex_resampler_destroy(srs);
		celt.celt_encoder_destroy(ce);
		celt.celt_mode_destroy(cm);
	}
}
