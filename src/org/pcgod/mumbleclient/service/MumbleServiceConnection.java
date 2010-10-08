package org.pcgod.mumbleclient.service;

import junit.framework.Assert;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

public class MumbleServiceConnection {
	ServiceConnection mServiceConn = new ServiceConnection() {
		public void onServiceConnected(final ComponentName className,
				final IBinder binder) {
			mService = ((MumbleService.LocalBinder) binder).getService();
		}

		public void onServiceDisconnected(final ComponentName arg0) {
			mService = null;
		}
	};
	protected MumbleService mService;
	protected final Context ctx;

	public MumbleServiceConnection(final Context ctx) {
		this.ctx = ctx;
	}

	public void bind() {
		Assert.assertNull(mService);

		final Intent intent = new Intent(ctx, MumbleService.class);
		ctx.bindService(intent, mServiceConn, Context.BIND_AUTO_CREATE);
	}

	public MumbleService getService() {
		Assert.assertNotNull(mService);
		return mService;
	}

	public void release() {
		ctx.unbindService(mServiceConn);
	}
}
