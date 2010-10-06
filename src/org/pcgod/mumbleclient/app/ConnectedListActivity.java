package org.pcgod.mumbleclient.app;

import junit.framework.Assert;

import org.pcgod.mumbleclient.service.MumbleService;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class ConnectedListActivity extends ListActivity {
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
