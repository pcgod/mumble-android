package org.pcgod.mumbleclient.service;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import junit.framework.Assert;

import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

/**
 * Thread safe buffer for audio data.
 *
 * Implements audio queue and decoding.
 *
 * @author pcgod, Rantanen
 *
 */
class AudioUser {

	private final User user;
	private final Queue<short[]> frames = new ConcurrentLinkedQueue<short[]>();
	private boolean destroyable = true;
	private final Object syncLock = new Object();

	private final static short[] zeroOut =
		new short[MumbleConnection.FRAME_SIZE];

	public AudioUser(final User user) {
		this.user = user;
	}

	public boolean addFrameToBuffer(PacketDataStream pds, final Object monitor) {

		Assert.assertFalse(destroyable);

		int packetHeader = pds.next();

		// Make sure this is supported voice packet.
		//
		// (Yes this check is included in MumbleConnection as well but I believe
		// it should be made here since the decoding support is built into this
		// class anyway. In theory only this class needs to know what packets
		// can be decoded.)
		int type = (packetHeader >> 5) & 0x7;
		if (type != MumbleConnection.UDPMESSAGETYPE_UDPVOICECELTALPHA &&
				type != MumbleConnection.UDPMESSAGETYPE_UDPVOICECELTBETA) {
			return false;
		}

		long session = pds.readLong();
		long sequence = pds.readLong();

		final byte[] data = new byte[128];
		int dataHeader;
		do {
			dataHeader = pds.next();
			int dataLength = dataHeader & 0x7f;
			if (dataLength > 0) {
				pds.dataBlock(data, dataLength);

				short[] out = new short[MumbleConnection.FRAME_SIZE];
				Native.celt_decode(AudioOutput.celtDecoder, data, dataLength, out);

				frames.add(out);

				synchronized(monitor) {
					monitor.notify();
				}
			}
		} while ((dataHeader & 0x80) > 0 && pds.isValid());

		return true;
	}

	public short[] getFrame() {
		return frames.poll();
	}

	public boolean canDestroy() {
		synchronized(syncLock) {
			return destroyable && frames.isEmpty();
		}
	}

	/**
	 * Alter the destroyable state of the AudioUser.
	 *
	 * Non-destroyable AudioUser will never return null from getFrame. Exposing
	 * this mechanism as public interface allows the AudioUser owner to perform
	 * synchronization involving the destroyable state.
	 *
	 * @param destroyable
	 */
	public void setDestroyable(boolean destroyable) {
		synchronized(syncLock) {
			this.destroyable = destroyable;
		}
	}
}
