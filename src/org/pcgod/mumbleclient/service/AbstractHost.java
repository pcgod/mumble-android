package org.pcgod.mumbleclient.service;

import org.pcgod.mumbleclient.Globals;

import android.os.RemoteException;

public abstract class AbstractHost {
	abstract class ProtocolMessage implements Runnable {
		@Override
		public final void run() {
			if (isDisabled()) {
				Globals.logWarn(this, "Ignoring message, Service is disconnected");
			}

			process();

			for (final IServiceObserver observer : getObservers()) {
				try {
					broadcast(observer);
				} catch (final RemoteException e) {
					Globals.logError(this,
						"Error while broadcasting service state",
						e);
				}
			}
		}

		protected abstract void broadcast(IServiceObserver observer)
			throws RemoteException;

		protected abstract Iterable<IServiceObserver> getObservers();

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
