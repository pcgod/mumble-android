package org.pcgod.mumbleclient;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.pcgod.mumbleclient.jni.SWIGTYPE_p_CELTDecoder;
import org.pcgod.mumbleclient.jni.SWIGTYPE_p_CELTMode;
import org.pcgod.mumbleclient.jni.celt;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

class AudioOutput implements Runnable {
	static SWIGTYPE_p_CELTDecoder celtDecoder;
	private final ConcurrentHashMap<User, AudioUser> outputMap = new ConcurrentHashMap<User, AudioUser>();
	private short[] out;
	private boolean running;
	private final AudioTrack at;
	private final SWIGTYPE_p_CELTMode celtMode;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition notEmpty = lock.newCondition();
	private final static int bufferSize = MumbleClient.FRAME_SIZE * 4;

	AudioOutput() {
		at = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
				MumbleClient.SAMPLE_RATE,
				AudioFormat.CHANNEL_CONFIGURATION_MONO,
				AudioFormat.ENCODING_PCM_16BIT, 32768, AudioTrack.MODE_STREAM);
		at.play();

		celtMode = celt.celt_mode_create(MumbleClient.SAMPLE_RATE,
				MumbleClient.FRAME_SIZE);
		celtDecoder = celt.celt_decoder_create(celtMode, 1);
	}

	public void addFrameToBuffer(final User u, final ByteBuffer packet,
			final int iSeq) {
		AudioUser au = outputMap.get(u);
		if (au == null) {
			au = new AudioUser(u);
			outputMap.put(u, au);
		}

		au.addFrameToBuffer(packet, iSeq);
		lock.lock();
		notEmpty.signal();
		lock.unlock();
	}

	@Override
	public void run() {
		android.os.Process
				.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		running = true;
		while (running) {
			final boolean mixed = mix(bufferSize);
			if (mixed) {
				at.write(out, 0, bufferSize);
			} else {
				try {
					lock.lock();
					if (outputMap.isEmpty()) {
						notEmpty.await();
					}
				} catch (final InterruptedException e) {
					e.printStackTrace();
					running = false;
				} finally {
					lock.unlock();
				}
			}
		}
	}

	private boolean mix(final int nsamp) {
		final ArrayList<AudioUser> mix = new ArrayList<AudioUser>();
		final ArrayList<User> del = new ArrayList<User>();

		for (final Enumeration<User> e = outputMap.keys(); e.hasMoreElements();) {
			final User u = e.nextElement();
			final AudioUser au = outputMap.get(u);
			if (au.needSamples(nsamp)) {
				mix.add(au);
			} else {
				del.add(u);
			}
		}

		if (!mix.isEmpty()) {
			out = new short[bufferSize];
			for (final AudioUser au : mix) {
				final short[] pfBuffer = au.pfBuffer;

				for (int i = 0; i < nsamp; ++i) {
					int x = out[i] + pfBuffer[i];
					if (x > Short.MAX_VALUE)
						x = Short.MAX_VALUE;
					else if (x < Short.MIN_VALUE)
						x = Short.MIN_VALUE;
					out[i] = (short) x;
//					out[i] += pfBuffer[i];
				}
			}
		}

		for (final User u : del) {
			removeBuffer(u);
		}

		return !mix.isEmpty();
	}

	private void removeBuffer(final User u) {
		outputMap.remove(u);
	}

	@Override
	protected final void finalize() {
		celt.celt_decoder_destroy(celtDecoder);
		celt.celt_mode_destroy(celtMode);
	}
}
