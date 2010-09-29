package org.pcgod.mumbleclient.service.model;

public class User {
	public static final int TALKINGSTATE_PASSIVE = 0;
	public static final int TALKINGSTATE_TALKING = 1;
	public static final int TALKINGSTATE_SHOUTING = 2;
	public static final int TALKINGSTATE_WHISPERING = 3;

	public int session;
	public String name;
	public int channel;
	public float averageAvailable;
	public int talkingState;

	@Override
	public final boolean equals(final Object o) {
		if (!(o instanceof User)) {
			return false;
		}
		return session == ((User) o).session;
	}

	@Override
	public final int hashCode() {
		return session;
	}

	@Override
	public final String toString() {
		return "User [session=" + session + ", name=" + name + ", channel="
				+ channel + "]";
	}
}
