package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.MumbleService;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * Base class for activities that want to access the MumbleService
 *
 * Note: Remember to consider ConnectedListActivity when modifying this class.
 *
 * @author Rantanen
 *
 */
public class ConnectedActivity extends Activity {
	ServiceConnection mServiceConn = new ServiceConnection() {
		public void onServiceConnected(
			final ComponentName className,
			final IBinder binder) {
			mService = ((MumbleService.LocalBinder) binder).getService();
			Log.i("Mumble", "mService set");

			mObserver = createServiceObserver();
			if (mObserver != null) {
				mService.registerObserver(mObserver);
			}

			onServiceBound();
		}

		public void onServiceDisconnected(final ComponentName arg0) {
			mService = null;
		}
	};
	protected MumbleService mService;
	protected IServiceObserver mObserver;

	protected IServiceObserver createServiceObserver() {
		return null;
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mObserver != null) {
			mService.unregisterObserver(mObserver);
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
}
