package org.pcgod.mumbleclient.service.model;

public class AccessToken {
	public String value;
	public final long id;
	public final long serverId;

	public AccessToken(final String value, final long id, final long serverId) {
		this.value = value;
		this.id = id;
		this.serverId = serverId;
	}

	@Override
	public String toString() {
		return this.value;
	}
}
