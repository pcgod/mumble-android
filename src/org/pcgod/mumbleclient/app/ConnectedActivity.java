package org.pcgod.mumbleclient.app;

import junit.framework.Assert;

import org.pcgod.mumbleclient.Globals;
import org.pcgod.mumbleclient.service.BaseServiceObserver;
import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.MumbleService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

/**
 * Base class for activities that want to access the MumbleService
 *
 * Note: Remember to consider ConnectedListActivity when modifying this class.
 *
 * @author Rantanen
 *
 */
public class ConnectedActivity extends Activity {
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
			mService = ((MumbleService.LocalBinder) binder).getService();
			Log.i("Mumble", "mService set");

			mInternalObserver = new ConnectedServiceObserver();
			mService.registerObserver(mInternalObserver);
			mObserver = createServiceObserver();
			if (mObserver != null) {
				mService.registerObserver(mObserver);
			}

			onServiceBound();
			connectionStateUpdated(mService.getConnectionState());
		}

		public void onServiceDisconnected(final ComponentName arg0) {
			mService = null;
		}
	};

	private IServiceObserver mInternalObserver;

	protected MumbleService mService;
	protected IServiceObserver mObserver;

	private void connectionStateUpdated(final int state) {
		switch (state) {
		case MumbleService.CONNECTION_STATE_CONNECTING:
			Log.i(Globals.LOG_TAG, String.format(
				"%s: Connecting",
				getClass().getName()));
			onConnecting();
			break;
		case MumbleService.CONNECTION_STATE_SYNCHRONIZING:
			Log.i(Globals.LOG_TAG, String.format(
				"%s: Synchronizing",
				getClass().getName()));
			onSynchronizing();
			break;
		case MumbleService.CONNECTION_STATE_CONNECTED:
			Log.i(Globals.LOG_TAG, String.format(
				"%s: Connected",
				getClass().getName()));
			onConnected();
			break;
		case MumbleService.CONNECTION_STATE_DISCONNECTED:
			Log.i(Globals.LOG_TAG, String.format(
				"%s: Disconnected",
				getClass().getName()));
			onDisconnected();
			break;
		default:
			Assert.fail("Unknown connection state");
		}
	}

	protected IServiceObserver createServiceObserver() {
		return null;
	}

	protected void onConnected() {
	}

	protected void onConnecting() {
	}

	protected void onDisconnected() {
		final String error = mService.getError();
		if (error != null) {
			Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
		}
		finish();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mInternalObserver != null) {
			mService.unregisterObserver(mInternalObserver);
			mInternalObserver = null;
		}
		if (mObserver != null) {
			mService.unregisterObserver(mObserver);
			mObserver = null;
		}
		unbindService(mServiceConn);
	}

	@Override
	protected void onResume() {
		super.onResume();
		final Intent intent = new Intent(this, MumbleService.class);
		bindService(intent, mServiceConn, BIND_AUTO_CREATE);
	}

	protected void onServiceBound() {
	}

	protected void onSynchronizing() {
	}
}
