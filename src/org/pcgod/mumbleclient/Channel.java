package org.pcgod.mumbleclient;

public class Channel {
	public int id;
	public String name;

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Channel))
			return false;
		return id == ((Channel) o).id;
	}

	@Override
	public String toString() {
		return "Channel [id=" + id + ", name=" + name + "]";
	}
}
