package org.pcgod.mumbleclient.service.model;

import java.io.Serializable;

import junit.framework.Assert;

public class User implements Serializable {
	private static final long serialVersionUID = 1L;

	public static final int TALKINGSTATE_PASSIVE = 0;
	public static final int TALKINGSTATE_TALKING = 1;
	public static final int TALKINGSTATE_SHOUTING = 2;
	public static final int TALKINGSTATE_WHISPERING = 3;

	public int session;
	public String name;
	public float averageAvailable;
	public int talkingState;

	private Channel channel;

	@Override
	public final boolean equals(final Object o) {
		if (!(o instanceof User)) {
			return false;
		}
		return session == ((User) o).session;
	}

	public final Channel getChannel() {
		return this.channel;
	}

	@Override
	public final int hashCode() {
		return session;
	}

	public void setChannel(final Channel newChannel) {
		// Moving user to another channel?
		// If so, remove the user from the original first.
		if (this.channel != null) {
			this.channel.userCount--;
		}

		// User should never leave channel without joining a new one?
		Assert.assertNotNull(newChannel);

		this.channel = newChannel;
		this.channel.userCount++;
	}

	@Override
	public final String toString() {
		return "User [session=" + session + ", name=" + name + ", channel=" +
				channel + "]";
	}
}
