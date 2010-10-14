package org.pcgod.mumbleclient.service;

import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.model.Message;
import org.pcgod.mumbleclient.service.model.User;

public interface MumbleConnectionHost {
	public enum ConnectionState {
		Disconnected, Connecting, Synchronizing, Connected, Disconnecting
	}

	public void channelAdded(Channel channel);

	public void channelRemoved(int channelId);

	public void channelUpdated(Channel channel);

	public void currentChannelChanged();

	public void currentUserUpdated();

	public void messageReceived(Message msg);

	public void messageSent(Message msg);

	public void setConnectionState(ConnectionState state);

	public void userAdded(User user);

	public void userRemoved(int userId);

	public void userUpdated(User user);
}
