package org.pcgod.mumbleclient.service;

import java.io.IOException;

import org.pcgod.mumbleclient.Globals;

import android.util.Log;

/**
 * Provides the general structure for the socket readers.
 *
 * @author Rantanen
 *
 */
public abstract class MumbleSocketReader implements Runnable {
	private final Object monitor;
	private boolean running;

	/**
	 * Constructs a new Reader instance
	 *
	 * @param monitor
	 *            The monitor that should be signaled when the thread is
	 *            quitting.
	 */
	public MumbleSocketReader(final Object monitor) {
		this.monitor = monitor;
		this.running = true;
	}

	/**
	 * The condition that must be fulfilled for the reader to continue running.
	 *
	 * @return True while the reader should keep processing the socket.
	 */
	public boolean isRunning() {
		return running;
	}

	@Override
	public void run() {
		try {
			while (isRunning()) {
				process();
			}
		} catch (final IOException ex) {
			Log.e(Globals.LOG_TAG, ex.toString());
		} finally {
			running = false;
			synchronized (monitor) {
				monitor.notifyAll();
			}
		}
	}

	/**
	 * A single processing step that reads and processes a message from the
	 * socket.
	 *
	 * @throws IOException
	 */
	protected abstract void process() throws IOException;
}
