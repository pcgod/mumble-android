package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.service.MumbleService;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
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
		public void onServiceDisconnected(ComponentName arg0) {
			mService = null;
		}

		public void onServiceConnected(ComponentName className, IBinder binder) {
			mService = ((MumbleService.LocalBinder)binder).getService();
			Log.i("Mumble", "mService set");
			onServiceBound();
		}
	};
	protected MumbleService mService;

	@Override
	protected void onPause() {
		super.onPause();
		unbindService(mServiceConn);
	}

	@Override
	protected void onResume() {
		super.onResume();
		Intent intent = new Intent(this, MumbleService.class);
		bindService(intent, mServiceConn, BIND_AUTO_CREATE);
	}

	protected void onServiceBound() { }
}
