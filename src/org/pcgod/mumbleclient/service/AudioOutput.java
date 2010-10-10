package org.pcgod.mumbleclient.service;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Audio output thread.
 * Handles the playback of UDP packets added with addFrameToBuffer.
 *
 * @author pcgod, Rantanen
 */
class AudioOutput implements Runnable {
	static long celtDecoder;
	private boolean shouldRun;
	private final AudioTrack at;
	private final long celtMode;
	private final int bufferSize;

	private final Map<User, AudioUser> userPackets = new ConcurrentHashMap<User, AudioUser>();

	public AudioOutput() {
		int minBufferSize = AudioTrack.getMinBufferSize(
			MumbleConnection.SAMPLE_RATE,
			AudioFormat.CHANNEL_CONFIGURATION_MONO,
			AudioFormat.ENCODING_PCM_16BIT);

		// Double the buffer size to reduce stuttering.
		minBufferSize *= 2;

		// Resolve the minimum frame count that fills the minBuffer requirement.
		final int frameCount = (int) Math.round(Math.ceil((double) minBufferSize /
														  MumbleConnection.FRAME_SIZE));

		bufferSize = frameCount * MumbleConnection.FRAME_SIZE;

		at = new AudioTrack(
			AudioManager.STREAM_MUSIC,
			MumbleConnection.SAMPLE_RATE,
			AudioFormat.CHANNEL_CONFIGURATION_MONO,
			AudioFormat.ENCODING_PCM_16BIT,
			bufferSize,
			AudioTrack.MODE_STREAM);

		celtMode = Native.celt_mode_create(
			MumbleConnection.SAMPLE_RATE,
			MumbleConnection.FRAME_SIZE);
		celtDecoder = Native.celt_decoder_create(celtMode, 1);

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

		// Get user and mark it as non-destroyable so we won't lose it after
		// we have acquired it.
		synchronized (userPackets) {
			user = userPackets.get(u);
			if (user == null) {
				user = new AudioUser(u);
				userPackets.put(u, user);
			}
			user.setDestroyable(false);
		}

		user.addFrameToBuffer(pds, userPackets);

		user.setDestroyable(true);
	}

	@Override
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
		final List<float[]> mix = new LinkedList<float[]>();
		final List<Entry<User, AudioUser>> del = new LinkedList<Entry<User, AudioUser>>();

		at.play();
		while (shouldRun) {
			mix.clear();
			del.clear();
			for (final Entry<User, AudioUser> pair : userPackets.entrySet()) {
				final float[] frame = pair.getValue().getFrame();
				if (frame != null) {
					mix.add(frame);
				} else {
					del.add(pair);
				}
			}

			// If there is output, play it now.
			if (mix.size() > 0) {
				// Reset mix buffer.
				Arrays.fill(out, 0);

				// Sum the buffers.
				for (final float[] userBuffer : mix) {
					for (int i = 0; i < out.length; i++) {
						out[i] += userBuffer[i];
					}
				}

				// Clip buffer for real output.
				for (int i = 0; i < MumbleConnection.FRAME_SIZE; i++) {
					clipOut[i] = (short) (Short.MAX_VALUE * (out[i] < -1.0f ? -1.0f
						: (out[i] > 1.0f ? 1.0f : out[i])));
				}

				at.write(clipOut, 0, MumbleConnection.FRAME_SIZE);

				// Don't spend time cleaning users at this point. Do so when
				// there's a pause in the playback.
				continue;
			}

			// Log.i(Globals.LOG_TAG, "Playback paused");

			// If there were empty users see if they can be destroyed.
			if (del.size() > 0) {
				int removedUsers = 0;
				synchronized (userPackets) {
					for (final Entry<User, AudioUser> pair : del) {
						if (pair.getValue().canDestroy()) {
							userPackets.remove(pair.getKey());
							removedUsers++;
						}
					}
				}
				if (removedUsers > 0) {
					Log.i(Globals.LOG_TAG, String.format(
						"AudioOutput: Removed %d users",
						removedUsers));
				}
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

	@Override
	protected final void finalize() {
		Native.celt_decoder_destroy(celtDecoder);
		Native.celt_mode_destroy(celtMode);
	}
}
