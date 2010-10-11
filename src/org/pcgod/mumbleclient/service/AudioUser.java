package org.pcgod.mumbleclient.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

import android.util.Log;

/**
 * Thread safe buffer for audio data.
 * Implements audio queue and decoding.
 *
 * @author pcgod, Rantanen
 */
class AudioUser {
	public interface PacketReadyHandler {
		public void packetReady(AudioUser user);
	}

	private final long celtMode;
	private final long celtDecoder;
	private final Queue<float[]> frames = new ConcurrentLinkedQueue<float[]>();
	float[] freeFrame = new float[MumbleConnection.FRAME_SIZE];
	float[] lastFrame;
	private final Queue<float[]> freeFrames = new ConcurrentLinkedQueue<float[]>();
	final byte[] data = new byte[128];
	private final User user;

	public AudioUser(final User user) {
		this.user = user;
		celtMode = Native.celt_mode_create(
			MumbleConnection.SAMPLE_RATE,
			MumbleConnection.FRAME_SIZE);
		celtDecoder = Native.celt_decoder_create(celtMode, 1);

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
		if (type != MumbleConnection.UDPMESSAGETYPE_UDPVOICECELTALPHA &&
			type != MumbleConnection.UDPMESSAGETYPE_UDPVOICECELTBETA) {
			return false;
		}

		/* long session = */pds.readLong();
		/* long sequence = */pds.readLong();

		int dataHeader;
		do {
			dataHeader = pds.next();
			final int dataLength = dataHeader & 0x7f;
			if (dataLength > 0) {
				pds.dataBlock(data, dataLength);

				final float[] out = acquireFrame();
				Native.celt_decode_float(celtDecoder, data, dataLength, out);
				frames.add(out);

				readyHandler.packetReady(this);
			}
		} while ((dataHeader & 0x80) > 0 && pds.isValid());

		return true;
	}

	public void freeFrame(final float[] frame) {
		synchronized (freeFrames) {
			freeFrames.add(frame);
		}
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
		final float[] frame = frames.poll();
		lastFrame = frame;
		return (lastFrame != null);
	}

	private float[] acquireFrame() {
		float[] frame = freeFrames.poll();

		if (frame == null) {
			frame = freeFrame;
			freeFrame = new float[MumbleConnection.FRAME_SIZE];
		}

		return frame;
	}

	@Override
	protected final void finalize() {
		Native.celt_decoder_destroy(celtDecoder);
		Native.celt_mode_destroy(celtMode);
	}
}
