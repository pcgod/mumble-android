package org.pcgod.mumbleclient.service;

interface IServiceObserver {
	
	void onChannelAdded();

	void onChannelRemoved();

	void onChannelUpdated();

	void onCurrentChannelChanged();
	
	void onCurrentUserUpdated();
	
	void onUserAdded();
	
	void onUserRemoved();
	
	void onUserUpdated();
	
	void onMessageReceived();

	void onMessageSent();
	
	/**
	 * Called when the connection state changes.
	 */
	void onConnectionStateChanged(int state);
}
