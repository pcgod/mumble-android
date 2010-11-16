package org.pcgod.mumbleclient.service;

import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.Message;
import org.pcgod.mumbleclient.service.model.User;

import android.os.IBinder;
import android.os.RemoteException;

public class BaseServiceObserver implements IServiceObserver {
	@Override
	public IBinder asBinder() {
		return null;
	}

	@Override
	public void onChannelAdded(final Channel channel) throws RemoteException {
	}

	@Override
	public void onChannelRemoved(final Channel channel) throws RemoteException {
	}

	@Override
	public void onChannelUpdated(final Channel channel) throws RemoteException {
	}

	@Override
	public void onConnectionStateChanged(final int state)
		throws RemoteException {
	}

	@Override
	public void onCurrentChannelChanged() throws RemoteException {
	}

	@Override
	public void onCurrentUserUpdated() throws RemoteException {
	}

	@Override
	public void onMessageReceived(final Message msg) throws RemoteException {
	}

	@Override
	public void onMessageSent(final Message msg) throws RemoteException {
	}

	@Override
	public void onUserAdded(final User user) throws RemoteException {
	}

	@Override
	public void onUserRemoved(final User user) throws RemoteException {
	}

	@Override
	public void onUserUpdated(final User user) throws RemoteException {
	}
}
