package org.pcgod.mumbleclient.service;

import org.pcgod.mumbleclient.service.model.User;
import org.pcgod.mumbleclient.service.model.Message;

interface IServiceObserver {
	void onChannelAdded();

	void onChannelRemoved();

	void onChannelUpdated();

	void onCurrentChannelChanged();
	
	void onCurrentUserUpdated();
	
	void onUserAdded(in User user);
	void onUserRemoved(in User user);
	void onUserUpdated(in User user);
	
	void onMessageReceived(in Message msg);
	void onMessageSent(in Message msg);
	
	/**
	 * Called when the connection state changes.
	 */
	void onConnectionStateChanged(int state);
}
