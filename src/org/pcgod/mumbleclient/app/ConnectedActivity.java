package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.app.ConnectedActivityLogic.Host;
import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.MumbleService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
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
	private final Host logicHost = new Host() {
		@Override
		public boolean bindService(
			final Intent intent,
			final ServiceConnection mServiceConn,
			final int bindAutoCreate) {
			return ConnectedActivity.this.bindService(
				intent,
				mServiceConn,
				bindAutoCreate);
		}

		@Override
		public IServiceObserver createServiceObserver() {
			return ConnectedActivity.this.createServiceObserver();
		}

		@Override
		public void finish() {
			ConnectedActivity.this.finish();
		}

		@Override
		public Context getApplicationContext() {
			return ConnectedActivity.this.getApplicationContext();
		}

		@Override
		public MumbleService getService() {
			return mService;
		}

		@Override
		public void onConnected() {
			ConnectedActivity.this.onConnected();
		}

		@Override
		public void onConnecting() {
			ConnectedActivity.this.onConnecting();
		}

		@Override
		public void onDisconnected() {
			ConnectedActivity.this.onDisconnected();
		}

		@Override
		public void onServiceBound() {
			ConnectedActivity.this.onServiceBound();
		}

		@Override
		public void onSynchronizing() {
			ConnectedActivity.this.onSynchronizing();
		}

		@Override
		public void setService(final MumbleService service) {
			mService = service;
		}

		@Override
		public void unbindService(final ServiceConnection mServiceConn) {
			ConnectedActivity.this.unbindService(mServiceConn);
		}
	};

	private final ConnectedActivityLogic logic = new ConnectedActivityLogic(
		logicHost);

	protected MumbleService mService;
	protected IServiceObserver mObserver;

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
		logic.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		logic.onResume();
	}

	protected void onServiceBound() {
	}

	protected void onSynchronizing() {
	}
}
