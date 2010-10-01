package org.pcgod.mumbleclient.service;

import org.pcgod.mumbleclient.service.model.Message;

public interface MumbleConnectionHost {
	public enum ConnectionState {
		Disconnected, Connecting, Connected, Disconnecting
	}

	public void setConnectionState(ConnectionState state);

	public void messageReceived(Message msg);
	public void messageSent(Message msg);

	public void channelsUpdated();
	public void userListUpdated();
	public void currentChannelChanged();

}
