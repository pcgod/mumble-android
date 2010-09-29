package org.pcgod.mumbleclient.service.model;

public class Channel {
	public int id;
	public String name;
	public int userCount;

	@Override
	public final boolean equals(final Object o) {
		if (!(o instanceof Channel)) {
			return false;
		}
		return id == ((Channel) o).id;
	}

	@Override
	public final int hashCode() {
		return id;
	}

	@Override
	public final String toString() {
		return "Channel [id=" + id + ", name=" + name + ", userCount="
				+ userCount + "]";
	}
}
