package org.pcgod.mumbleclient.service.audio;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.MumbleProtocol;
import org.pcgod.mumbleclient.service.PacketDataStream;
import org.pcgod.mumbleclient.service.model.User;

import android.util.Log;

/**
 * Thread safe buffer for audio data.
 * Implements audio queue and decoding.
 *
 * @author pcgod, Rantanen
 */
public class AudioUser {
	public interface PacketReadyHandler {
		public void packetReady(AudioUser user);
	}

	private final boolean useJitterBuffer;

	private final Object jbLock;
	private final long jitterBuffer;
	private final int[] currentTimestamp;
	private final Queue<Native.JitterBufferPacket> normalBuffer;

	private final long celtMode;
	private final long celtDecoder;
	private final Queue<byte[]> dataArrayPool = new ConcurrentLinkedQueue<byte[]>();
	float[] lastFrame = new float[MumbleProtocol.FRAME_SIZE];
	private final User user;

	private int missedFrames = 0;

	public AudioUser(final User user, final boolean useJitterBuffer) {
		this.user = user;
		this.useJitterBuffer = useJitterBuffer;

		celtMode = Native.celt_mode_create(
			MumbleProtocol.SAMPLE_RATE,
			MumbleProtocol.FRAME_SIZE);
		celtDecoder = Native.celt_decoder_create(celtMode, 1);

		// Initialize one of the buffers.
		if (useJitterBuffer) {
			jbLock = new Object();
			currentTimestamp = new int[1];
			jitterBuffer = Native.jitter_buffer_init(MumbleProtocol.FRAME_SIZE);
			Native.jitter_buffer_ctl(
				jitterBuffer,
				0,
				new int[] { 5 * MumbleProtocol.FRAME_SIZE });

			normalBuffer = null;
		} else {
			normalBuffer = new ConcurrentLinkedQueue<Native.JitterBufferPacket>();

			jitterBuffer = 0;
			currentTimestamp = null;
			jbLock = null;
		}

		Log.i(Globals.LOG_TAG, "AudioUser created");
	}

	public boolean addFrameToBuffer(
		final PacketDataStream pds,
		final PacketReadyHandler readyHandler) {

		final int packetHeader = pds.next();

		// Make sure this is supported voice packet.
		//
		// (Yes this check is included in MumbleConnection as well but I believe
		// it should be made here since the decoding support is built into this
		// class anyway. In theory only this class needs to know what packets
		// can be decoded.)
		final int type = (packetHeader >> 5) & 0x7;
		if (type != MumbleProtocol.UDPMESSAGETYPE_UDPVOICECELTALPHA &&
			type != MumbleProtocol.UDPMESSAGETYPE_UDPVOICECELTBETA) {
			return false;
		}

		/* long session = */pds.readLong();
		final long sequence = pds.readLong();

		int dataHeader;
		int frameCount = 0;

		byte[] data = null;
		// Jitter buffer can use one data array to pass all the packets to the buffer.
		if (useJitterBuffer) {
			data = acquireDataArray();
		}
		do {
			dataHeader = pds.next();
			final int dataLength = dataHeader & 0x7f;
			if (dataLength > 0) {

				// If not using jitter buffer acquire data array for each packet.
				// They are released when dequeueing them fromt he buffer.
				if (!useJitterBuffer) {
					data = acquireDataArray();
				}
				pds.dataBlock(data, dataLength);

				final Native.JitterBufferPacket jbp = new Native.JitterBufferPacket();
				jbp.data = data;
				jbp.len = dataLength;

				if (useJitterBuffer) {
					jbp.timestamp = (short) (sequence + frameCount) *
									MumbleProtocol.FRAME_SIZE;
					jbp.span = MumbleProtocol.FRAME_SIZE;

					synchronized (jbLock) {
						Native.jitter_buffer_put(jitterBuffer, jbp);
					}
				} else {
					normalBuffer.add(jbp);
				}

				readyHandler.packetReady(this);
				frameCount++;

			}
		} while ((dataHeader & 0x80) > 0 && pds.isValid());

		if (useJitterBuffer) {
			freeDataArray(data);
		}
		return true;
	}

	public void freeDataArray(final byte[] data) {
		dataArrayPool.add(data);
	}

	public User getUser() {
		return this.user;
	}

	/**
	 * Checks if this user has frames and sets lastFrame.
	 *
	 * @return
	 */
	public boolean hasFrame() {
		byte[] data = null;
		int dataLength = 0;

		Native.JitterBufferPacket jbp;

		if (useJitterBuffer) {
			jbp = new Native.JitterBufferPacket();
			jbp.data = acquireDataArray();
			jbp.len = jbp.data.length;

			synchronized (jbLock) {
				if (Native.jitter_buffer_get(
					jitterBuffer,
					jbp,
					MumbleProtocol.FRAME_SIZE,
					currentTimestamp) == 0) {

					data = jbp.data;
					dataLength = jbp.len;
					missedFrames = 0;
				} else {
					missedFrames++;
				}

				Native.jitter_buffer_update_delay(jitterBuffer, null, null);
			}

		} else {
			jbp = normalBuffer.poll();
			if (jbp != null) {
				data = jbp.data;
				dataLength = jbp.len;
				missedFrames = 0;
			} else {
				missedFrames++;
			}
		}

		Native.celt_decode_float(celtDecoder, data, dataLength, lastFrame);

		if (data != null) {
			freeDataArray(data);
		}

		if (useJitterBuffer) {
			synchronized (jbLock) {
				Native.jitter_buffer_tick(jitterBuffer);
			}
		}

		return (missedFrames < 10);
	}

	private byte[] acquireDataArray() {
		byte[] data = dataArrayPool.poll();

		if (data == null) {
			data = new byte[128];
		}

		return data;
	}

	@Override
	protected final void finalize() {
		Native.celt_decoder_destroy(celtDecoder);
		Native.celt_mode_destroy(celtMode);
		Native.jitter_buffer_destroy(jitterBuffer);
	}
}
