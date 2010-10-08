package org.pcgod.mumbleclient.service.model;

import java.io.Serializable;

public class Message implements Serializable {
	public enum Direction {
		Sent, Received
	}

	private static final long serialVersionUID = 1L;

	public String message;
	public String sender;
	public User actor;
	public Channel channel;
	public long timestamp;
	public int channelIds;
	public int treeIds;

	public Direction direction;
}
