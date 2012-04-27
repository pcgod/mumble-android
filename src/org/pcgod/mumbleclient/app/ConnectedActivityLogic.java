package org.pcgod.mumbleclient.app;

import junit.framework.Assert;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.service.BaseServiceObserver;
import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.MumbleService;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

public class ConnectedActivityLogic {
	public interface Host {
		boolean bindService(Intent intent, ServiceConnection conn, int flags);

		IServiceObserver createServiceObserver();

		void finish();

		Context getApplicationContext();

		MumbleService getService();

		void onConnected();

		void onConnecting();

		void onDisconnected();

		void onServiceBound();

		void onSynchronizing();

		void setService(MumbleService service);

		void unbindService(ServiceConnection conn);
	}

	class ConnectedServiceObserver extends BaseServiceObserver {
		@Override
		public void onConnectionStateChanged(final int state) throws RemoteException {
			connectionStateUpdated(state);
		}
	}

	ServiceConnection mServiceConn = new ServiceConnection() {
		public void onServiceConnected(
			final ComponentName className,
			final IBinder binder) {
			if (paused) {
				// Don't bother doing anything if the activity is already paused.
				return;
			}

			final MumbleService service = ((MumbleService.LocalBinder) binder).getService();
			mHost.setService(service);

			mInternalObserver = new ConnectedServiceObserver();
			service.registerObserver(mInternalObserver);
			mObserver = mHost.createServiceObserver();
			if (mObserver != null) {
				service.registerObserver(mObserver);
			}

			mHost.onServiceBound();
			connectionStateUpdated(service.getConnectionState());
		}

		public void onServiceDisconnected(final ComponentName arg0) {
			mHost.setService(null);
		}
	};

	private IServiceObserver mInternalObserver;
	private final Host mHost;
	private boolean paused = false;

	protected IServiceObserver mObserver;

	public ConnectedActivityLogic(final Host host) {
		this.mHost = host;
	}

	public void onPause() {
		paused = true;

		if (mInternalObserver != null) {
			mHost.getService().unregisterObserver(mInternalObserver);
			mInternalObserver = null;
		}

		if (mObserver != null) {
			mHost.getService().unregisterObserver(mObserver);
			mObserver = null;
		}

		mHost.unbindService(mServiceConn);
	}

	public void onResume() {
		paused = false;

		final Intent intent = new Intent(
			mHost.getApplicationContext(),
			MumbleService.class);
		mHost.bindService(intent, mServiceConn, Context.BIND_AUTO_CREATE);
	}

	private void connectionStateUpdated(final int state) {
		switch (state) {
		case MumbleService.CONNECTION_STATE_CONNECTING:
			Globals.logInfo(this, "Connecting");
			mHost.onConnecting();
			break;
		case MumbleService.CONNECTION_STATE_SYNCHRONIZING:
			Globals.logInfo(this, "Synchronizing");
			mHost.onSynchronizing();
			break;
		case MumbleService.CONNECTION_STATE_CONNECTED:
			Globals.logInfo(this, "Connected");
			mHost.onConnected();
			break;
		case MumbleService.CONNECTION_STATE_DISCONNECTED:
			Globals.logInfo(this, "Disconnected");
			mHost.onDisconnected();
			break;
		default:
			Assert.fail("Unknown connection state");
		}
	}
}
