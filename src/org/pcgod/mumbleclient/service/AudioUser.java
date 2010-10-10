package org.pcgod.mumbleclient.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.Assert;

import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

/**
 * Thread safe buffer for audio data.
 * Implements audio queue and decoding.
 *
 * @author pcgod, Rantanen
 */
class AudioUser {
	private final long celtMode;
	private final long celtDecoder;
	private final User user;
	private final Queue<float[]> frames = new ConcurrentLinkedQueue<float[]>();
	private boolean destroyable = true;
	private final Object syncLock = new Object();

	public AudioUser(final User user) {
		this.user = user;

		celtMode = Native.celt_mode_create(
			MumbleConnection.SAMPLE_RATE,
			MumbleConnection.FRAME_SIZE);
		celtDecoder = Native.celt_decoder_create(celtMode, 1);
	}

	public boolean addFrameToBuffer(
		final PacketDataStream pds,
		final Object monitor) {
		Assert.assertFalse(destroyable);

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

		final byte[] data = new byte[128];
		int dataHeader;
		do {
			dataHeader = pds.next();
			final int dataLength = dataHeader & 0x7f;
			if (dataLength > 0) {
				pds.dataBlock(data, dataLength);

				final float[] out = new float[MumbleConnection.FRAME_SIZE];
				Native.celt_decode_float(celtDecoder, data, dataLength, out);

				frames.add(out);

				synchronized (monitor) {
					monitor.notify();
				}
			}
		} while ((dataHeader & 0x80) > 0 && pds.isValid());

		return true;
	}

	public boolean canDestroy() {
		synchronized (syncLock) {
			return destroyable && frames.isEmpty();
		}
	}

	public float[] getFrame() {
		return frames.poll();
	}

	/**
	 * Alter the destroyable state of the AudioUser.
	 * Non-destroyable AudioUser will never return null from getFrame. Exposing
	 * this mechanism as public interface allows the AudioUser owner to perform
	 * synchronization involving the destroyable state.
	 *
	 * @param destroyable
	 */
	public void setDestroyable(final boolean destroyable) {
		synchronized (syncLock) {
			this.destroyable = destroyable;
		}
	}

	@Override
	protected final void finalize() {
		Native.celt_decoder_destroy(celtDecoder);
		Native.celt_mode_destroy(celtMode);
	}
}
