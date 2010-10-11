package org.pcgod.mumbleclient.service.model;

import java.io.Serializable;

public class Message implements Serializable {
	public static final int DIRECTION_SENT = 0;
	public static final int DIRECTION_RECEIVED = 1;

	private static final long serialVersionUID = 1L;

	public String message;
	public String sender;
	public User actor;
	public Channel channel;
	public long timestamp;
	public int channelIds;
	public int treeIds;

	public int direction;
}
