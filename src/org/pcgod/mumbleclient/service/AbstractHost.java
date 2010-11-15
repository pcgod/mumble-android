package org.pcgod.mumbleclient.service;

import org.pcgod.mumbleclient.Globals;

import android.util.Log;

public abstract class AbstractHost {
	abstract class ProtocolMessage implements Runnable {
		@Override
		public final void run() {
			if (isDisabled()) {
				Log.w(
					Globals.LOG_TAG,
					"Ignoring message, Service is disconnected");
			}

			process();
			broadcast(null);
		}

		protected abstract void broadcast(IServiceObserver observer);

		protected abstract void process();
	}

	boolean disabled = false;

	public void disable() {
		this.disabled = true;
	}

	public boolean isDisabled() {
		return disabled;
	}
}
