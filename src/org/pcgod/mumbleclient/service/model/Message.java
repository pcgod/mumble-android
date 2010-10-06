package org.pcgod.mumbleclient.service.model;

import java.io.Serializable;

public class Message implements Serializable {

	private static final long serialVersionUID = 1L;

	public enum Direction { Sent, Received }

	public String message;
	public String sender;
	public User actor;
	public Channel channel;
	public long timestamp;
	public int channelIds;
	public int treeIds;

	public Direction direction;
}
