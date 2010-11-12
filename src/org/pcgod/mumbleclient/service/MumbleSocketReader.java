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
public abstract class MumbleSocketReader {
	private final Object monitor;
	private boolean running;
	private final Thread thread;

	protected Runnable runnable = new Runnable() {
		@Override
		public void run() {
			try {
				while (isRunning()) {
					process();
				}
			} catch (final IOException ex) {
				// If we aren't running, exception is expected.
				if (isRunning()) {
					Log.e(Globals.LOG_TAG, "Error reading socket", ex);
				}
			} finally {
				running = false;
				synchronized (monitor) {
					monitor.notifyAll();
				}
			}
		}
	};

	/**
	 * Constructs a new Reader instance
	 *
	 * @param monitor
	 *            The monitor that should be signaled when the thread is
	 *            quitting.
	 */
	public MumbleSocketReader(final Object monitor, final String name) {
		this.monitor = monitor;
		this.running = true;
		this.thread = new Thread(runnable, name);
	}

	/**
	 * The condition that must be fulfilled for the reader to continue running.
	 *
	 * @return True while the reader should keep processing the socket.
	 */
	public boolean isRunning() {
		return running && thread.isAlive();
	}

	public void start() {
		this.thread.start();
	}

	public void stop() {
		this.thread.interrupt();
		try {
			this.thread.join();
		} catch (final InterruptedException e) {
			Log.w(Globals.LOG_TAG, e);
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
