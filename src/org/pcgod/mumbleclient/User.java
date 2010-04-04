package org.pcgod.mumbleclient;

public class User {
	public int session;
	public String name;
	public int channel;

	@Override
	public int hashCode() {
		return session;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof User))
			return false;
		return session == ((User) o).session;
	}

	@Override
	public String toString() {
		return "User [session=" + session + ", name=" + name + ", channel="
				+ channel + "]";
	}
}
