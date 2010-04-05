package org.pcgod.mumbleclient;

public class User {
	public int session;
	public String name;
	public int channel;

	@Override
	public final int hashCode() {
		return session;
	}

	@Override
	public final boolean equals(final Object o) {
		if (!(o instanceof User)) {
			return false;
		}
		return session == ((User) o).session;
	}

	@Override
	public final String toString() {
		return "User [session=" + session + ", name=" + name + ", channel="
				+ channel + "]";
	}
}
