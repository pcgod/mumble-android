package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.app.ConnectedActivityLogic.Host;
import org.pcgod.mumbleclient.service.IServiceObserver;
import org.pcgod.mumbleclient.service.MumbleService;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.widget.Toast;

/**
 * Base class for list activities that want to access the MumbleService
 *
 * Note: Remember to consider ConnectedListActivity when modifying this class.
 *
 * @author Rantanen
 *
 */
public class ConnectedListActivity extends ListActivity {
	private final Host logicHost = new Host() {
		@Override
		public boolean bindService(
			final Intent intent,
			final ServiceConnection mServiceConn,
			final int bindAutoCreate) {
			return ConnectedListActivity.this.bindService(
				intent,
				mServiceConn,
				bindAutoCreate);
		}

		@Override
		public IServiceObserver createServiceObserver() {
			return ConnectedListActivity.this.createServiceObserver();
		}

		@Override
		public void finish() {
			ConnectedListActivity.this.finish();
		}

		@Override
		public Context getApplicationContext() {
			return ConnectedListActivity.this.getApplicationContext();
		}

		@Override
		public MumbleService getService() {
			return mService;
		}

		@Override
		public void onConnected() {
			ConnectedListActivity.this.onConnected();
		}

		@Override
		public void onConnecting() {
			ConnectedListActivity.this.onConnecting();
		}

		@Override
		public void onDisconnected() {
			ConnectedListActivity.this.onDisconnected();
		}

		@Override
		public void onServiceBound() {
			ConnectedListActivity.this.onServiceBound();
		}

		@Override
		public void onSynchronizing() {
			ConnectedListActivity.this.onSynchronizing();
		}

		@Override
		public void setService(final MumbleService service) {
			mService = service;
		}

		@Override
		public void unbindService(final ServiceConnection mServiceConn) {
			ConnectedListActivity.this.unbindService(mServiceConn);
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
