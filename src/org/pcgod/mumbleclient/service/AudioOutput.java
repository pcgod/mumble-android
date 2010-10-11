package org.pcgod.mumbleclient.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.pcgod.mumbleclient.service.AudioUser.PacketReadyHandler;
import org.pcgod.mumbleclient.service.model.User;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * Audio output thread.
 * Handles the playback of UDP packets added with addFrameToBuffer.
 *
 * @author pcgod, Rantanen
 */
class AudioOutput implements Runnable {
	private final PacketReadyHandler packetReadyHandler = new PacketReadyHandler() {
		@Override
		public void packetReady(final AudioUser user) {
			synchronized (userPackets) {
				if (!userPackets.containsKey(user.getUser())) {
					userPackets.put(user.getUser(), user);
					userPackets.notify();
				}
			}
		}
	};

	private boolean shouldRun;
	private final AudioTrack at;
	private final int bufferSize;

	final Map<User, AudioUser> userPackets = new HashMap<User, AudioUser>();
	private final Map<User, AudioUser> users = new HashMap<User, AudioUser>();

	public AudioOutput() {
		int minBufferSize = AudioTrack.getMinBufferSize(
			MumbleConnection.SAMPLE_RATE,
			AudioFormat.CHANNEL_CONFIGURATION_MONO,
			AudioFormat.ENCODING_PCM_16BIT);

		// Double the buffer size to reduce stuttering.
		minBufferSize *= 2;

		// Resolve the minimum frame count that fills the minBuffer requirement.
		final int frameCount = (int) Math.ceil((double) minBufferSize /
											   MumbleConnection.FRAME_SIZE);

		bufferSize = frameCount * MumbleConnection.FRAME_SIZE;

		at = new AudioTrack(
			AudioManager.STREAM_MUSIC,
			MumbleConnection.SAMPLE_RATE,
			AudioFormat.CHANNEL_CONFIGURATION_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
			bufferSize,
			AudioTrack.MODE_STREAM);

		// Set this here so this.start(); this.shouldRun = false; doesn't
		// result in run() setting shouldRun to true afterwards and continuing
		// running.
		shouldRun = true;
	}

	public void addFrameToBuffer(
		final User u,
		final PacketDataStream pds,
		final int flags) {

		AudioUser user;

		user = users.get(u);
		if (user == null) {
			user = new AudioUser(u);
			users.put(u, user);
			// Don't add the user to userPackets yet. The collection should
			// have only users with ready frames. Since this method is
			// called only from the TCP connection thread it will never
			// create a new AudioUser while a previous one is still decoding.
		}

		user.addFrameToBuffer(pds, packetReadyHandler);
	}

	public void run() {
		android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
		try {
			audioLoop();
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void stop() {
		shouldRun = false;
		synchronized (userPackets) {
			userPackets.notify();
		}
	}

	private void audioLoop() throws InterruptedException {
		final float[] out = new float[MumbleConnection.FRAME_SIZE];
		final short[] clipOut = new short[MumbleConnection.FRAME_SIZE];
		final List<AudioUser> mix = new LinkedList<AudioUser>();

		at.play();
		while (shouldRun) {
			mix.clear();

			synchronized (userPackets) {
				final Iterator<AudioUser> i = userPackets.values().iterator();
				while (i.hasNext()) {
					final AudioUser user = i.next();
					if (user.hasFrame()) {
						mix.add(user);
					} else {
						i.remove();
					}
				}
			}

			// If there is output, play it now.
			if (mix.size() > 0) {
				// Reset mix buffer.
				Arrays.fill(out, 0);

				// Sum the buffers.
				for (final AudioUser user : mix) {
					for (int i = 0; i < out.length; i++) {
						out[i] += user.lastFrame[i];
					}
				}

				// Clip buffer for real output.
				for (int i = 0; i < MumbleConnection.FRAME_SIZE; i++) {
					clipOut[i] = (short) (Short.MAX_VALUE * (out[i] < -1.0f ? -1.0f
						: (out[i] > 1.0f ? 1.0f : out[i])));
				}

				at.write(clipOut, 0, MumbleConnection.FRAME_SIZE);

			}

			// Wait for more input.
			synchronized (userPackets) {
				while (shouldRun && userPackets.isEmpty()) {
					userPackets.wait();
				}
			}
		}
		at.flush();
		at.stop();
	}
}
