package org.pcgod.mumbleclient.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.pcgod.mumbleclient.jni.Native;
import org.pcgod.mumbleclient.service.model.User;

class AudioOutput implements Runnable {
	static long celtDecoder;
	private final ConcurrentHashMap<User, AudioUser> outputMap = new ConcurrentHashMap<User, AudioUser>();
	private final short[] out;
	private final short[] tmpOut;
	private boolean running;
	//private final AudioTrack at;
	private final long celtMode;
	private final ReentrantLock lock = new ReentrantLock();
	private final Condition notEmpty = lock.newCondition();
	private final ArrayList<AudioUser> mix = new ArrayList<AudioUser>();
	private final ArrayList<User> del = new ArrayList<User>();
	private static int bufferSize = MumbleConnection.FRAME_SIZE;

	AudioOutput() {
//		double minbuffer = Math.max(AudioTrack
//				.getMinBufferSize(MumbleConnection.SAMPLE_RATE,
//						AudioFormat.CHANNEL_CONFIGURATION_MONO,
//						AudioFormat.ENCODING_PCM_16BIT), bufferSize);
//		Log.i("mumbleclient", "buffer size: " + minbuffer);
//		bufferSize = (int) (Math.ceil(minbuffer / MumbleConnection.FRAME_SIZE) * MumbleConnection.FRAME_SIZE);
//		Log.i("mumbleclient", "new buffer size: " + bufferSize);

//		at = new AudioTrack(AudioManager.STREAM_MUSIC,
//				MumbleConnection.SAMPLE_RATE,
//				AudioFormat.CHANNEL_CONFIGURATION_MONO,
//				AudioFormat.ENCODING_PCM_16BIT, bufferSize * 20,
//				AudioTrack.MODE_STREAM);
		out = new short[bufferSize];
		tmpOut = new short[bufferSize];
		celtMode = Native.celt_mode_create(MumbleConnection.SAMPLE_RATE,
				MumbleConnection.FRAME_SIZE);
		celtDecoder = Native.celt_decoder_create(celtMode, 1);
	}

	public void addFrameToBuffer(final User u, final PacketDataStream pds,
			final int flags) {
		AudioUser au = outputMap.get(u);
		if (au == null) {
			au = new AudioUser(u);
			outputMap.put(u, au);
		}

		au.addFrameToBuffer(pds, flags);
//		lock.lock();
//		notEmpty.signal();
//		lock.unlock();
	}

	@Override
	public void run() {
		//android.os.Process
		//		.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

		//at.play();
		running = true;
		while (running) {
//			final boolean mixed = mix(bufferSize);
//			if (mixed) {
//				at.write(out, 0, bufferSize);
			//at.flush();
//			} else {
			try {
				lock.lock();
				if (outputMap.isEmpty()) {
//						at.stop();
					notEmpty.await();
				}
			} catch (final InterruptedException e) {
				e.printStackTrace();
				running = false;
			} finally {
				lock.unlock();
//					at.play();
			}
//			}
			lock.lock();
			try {
				notEmpty.await();
			} catch (final InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			lock.unlock();
		}
//		at.stop();
//		at.release();
	}

	private boolean mix(final int nsamp) {
		mix.clear();
		del.clear();

		for (final Enumeration<User> e = outputMap.keys(); e.hasMoreElements();) {
			final User u = e.nextElement();
			final AudioUser au = outputMap.get(u);
			if (au.needSamples(nsamp)) {
				mix.add(au);
			} else {
				del.add(u);
			}
		}

		Arrays.fill(tmpOut, (short) 0);
		Arrays.fill(out, (short) 0);
		if (!mix.isEmpty()) {
			for (final AudioUser au : mix) {
				final short[] pfBuffer = au.pfBuffer;

				for (int i = 0; i < nsamp; ++i) {
					tmpOut[i] += pfBuffer[i];
				}
			}

			for (int i = 0; i < nsamp; ++i) {
				//out[i] = (short)(Short.MAX_VALUE * (tmpOut[i] < -1.0f ? -1.0f : (tmpOut[i] > 1.0f ? 1.0f : tmpOut[i])));
				//out[i] = (short)(Short.MAX_VALUE * tmpOut[i]);
				out[i] = tmpOut[i];
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
		Native.celt_decoder_destroy(celtDecoder);
		Native.celt_mode_destroy(celtMode);
	}
}
