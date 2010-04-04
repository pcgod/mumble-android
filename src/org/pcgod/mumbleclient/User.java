package org.pcgod.mumbleclient;

public class User {
	public int session;
	public String name;

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
		return "User [name=" + name + ", session=" + session + "]";
	}
}
