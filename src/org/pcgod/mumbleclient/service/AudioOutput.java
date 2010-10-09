package org.pcgod.mumbleclient.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

class AudioOutput implements Runnable {
	static long celtDecoder;
	private boolean shouldRun;
	private final AudioTrack at;
	private final long celtMode;
	private final int bufferSize;

	private final Map<User, Queue<short[]>> userPackets =
		new ConcurrentHashMap<User, Queue<short[]>>();
	private int lastPacket = 0;
	private final Object packetLock = new Object();

	public AudioOutput() {
		int minBufferSize = AudioTrack.getMinBufferSize(
				MumbleConnection.SAMPLE_RATE,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT);

		// Double the buffer size to reduce stuttering.
		minBufferSize *= 2;

		// Resolve the minimum frame count that fills the minBuffer requirement.
		final int frameCount = (int)Math.round(Math.ceil(
				(double)minBufferSize/MumbleConnection.FRAME_SIZE));

		bufferSize = frameCount * MumbleConnection.FRAME_SIZE;

		at = new AudioTrack(AudioManager.STREAM_MUSIC,
				MumbleConnection.SAMPLE_RATE,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, bufferSize,
				AudioTrack.MODE_STREAM);

		celtMode = Native.celt_mode_create(MumbleConnection.SAMPLE_RATE,
				MumbleConnection.FRAME_SIZE);
		celtDecoder = Native.celt_decoder_create(celtMode, 1);

		// Set this here so this.start(); this.shouldRun = false; doesn't
		// result in run() setting shouldRun to true afterwards and continuing
		// running.
		shouldRun = true;
	}

	public void addFrameToBuffer(final User u, final PacketDataStream pds,
			final int flags) {

		Queue<short[]> queue;

		// addFrameToBuffer should only ever be called from MumbleConnection
		// thread and no other thread should modify the userPackets map.
		// For this reason the get/put shouldn't require synchronization.
		queue = userPackets.get(u);
		if (queue == null) {
			queue = new ConcurrentLinkedQueue<short[]>();
			userPackets.put(u, queue);
		}

		decode(pds, queue);
	}

	@Override
	public void run() {
		try {
			audioLoop();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void stop() {
		shouldRun = false;
		synchronized(packetLock) {
			packetLock.notify();
		}
	}

	private void audioLoop() throws InterruptedException {
		double[] out = new double[MumbleConnection.FRAME_SIZE];
		short[] clipOut = new short[MumbleConnection.FRAME_SIZE];
		List<short[]> mix = new LinkedList<short[]>();

		at.play();
		while (shouldRun) {

			int packetSnapshot;
			synchronized(packetLock) {
				packetSnapshot = lastPacket;
			}

			mix.clear();
			for (Queue<short[]> queue : userPackets.values()) {
				short[] buffer = queue.poll();
				if (buffer != null)
					mix.add(buffer);
			}

			// If there is no output, wait for some and try again.
			if (mix.size() == 0) {
//				// if audio is playing stop it while we wait for more
//				// audio packets.
//				if (at.getplaystate() == audiotrack.playstate_playing) {
//					at.flush();
//					at.stop();
//				}

				synchronized(packetLock) {
					while (shouldRun && lastPacket == packetSnapshot) {
						packetLock.wait();
					}
				}

				// Restart audio if we are still running before re-starting
				// the loop again.
//				if (shouldRun) at.play();
				continue;
			}

			// We got input. Mix it and play. Use the first buffer as base
			// and add rest of the buffers to that one.
			//out = mix.remove(0);
			short[] firstBuffer = mix.remove(0);
			for (int i = 0; i < out.length; i++) {
				out[i] = firstBuffer[i];
			}
			for (short[] userBuffer : mix) {
				for (int i = 0; i < out.length; i++) {
					out[i] += userBuffer[i];
				}
			}

			// Clip buffer for real output.
			for (int i = 0; i < MumbleConnection.FRAME_SIZE; i++) {
				clipOut[i] = (short)Math.round(Math.max(Math.min(out[i], Short.MAX_VALUE), Short.MIN_VALUE));
			}

//			for (int i = 0; i < MumbleConnection.FRAME_SIZE; i++) {
//				out[i] = (short)Math.round(Math.sin(((double)i/48000)*400*2*3.14)*Short.MAX_VALUE);
//			}

			at.write(clipOut, 0, MumbleConnection.FRAME_SIZE);
		}
		at.flush();
		at.stop();
	}

	@Override
	protected final void finalize() {
		Native.celt_decoder_destroy(celtDecoder);
		Native.celt_mode_destroy(celtMode);
	}

	private void decode(PacketDataStream pds, Queue<short[]> queue) {
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

			final byte[] tmp = new byte[256];
			header = 0;
			do {
				header = pds.next();
				if (header > 0) {
					final int len = header & 0x7f;
					pds.dataBlock(tmp, len);
					short[] out = new short[MumbleConnection.FRAME_SIZE];

					Native.celt_decode(AudioOutput.celtDecoder, tmp, len, out);
					queue.add(out);

					synchronized(packetLock) {
						lastPacket++;
						packetLock.notify();
					}
				}
			} while (((header & 0x80) > 0) && pds.isValid());
		}

	}
}
