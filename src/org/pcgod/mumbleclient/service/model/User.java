package org.pcgod.mumbleclient.service.model;

import junit.framework.Assert;
import android.os.Parcel;
import android.os.Parcelable;

public class User implements Parcelable {
	public static final Parcelable.Creator<User> CREATOR = new Creator<User>() {
		@Override
		public User createFromParcel(final Parcel source) {
			return new User(source);
		}

		@Override
		public User[] newArray(final int size) {
			return new User[size];
		}
	};

	public static final int TALKINGSTATE_PASSIVE = 0;
	public static final int TALKINGSTATE_TALKING = 1;
	public static final int TALKINGSTATE_SHOUTING = 2;
	public static final int TALKINGSTATE_WHISPERING = 3;

	public static final int USERSTATE_NONE = 0;
	public static final int USERSTATE_MUTED = 1;
	public static final int USERSTATE_DEAFENED = 2;

	public int session;
	public String name;
	public float averageAvailable;
	public int talkingState;
	public int userState;
	public boolean isCurrent;

	public boolean muted;
	public boolean deafened;

	private Channel channel;

	public User() {
	}

	public User(final Parcel in) {
		readFromParcel(in);
	}

	@Override
	public int describeContents() {
		return 0;
	}

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

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(0); // Version

		dest.writeInt(session);
		dest.writeString(name);
		dest.writeFloat(averageAvailable);
		dest.writeInt(talkingState);
		dest.writeBooleanArray(new boolean[] { isCurrent, muted, deafened });
		dest.writeParcelable(channel, 0);
	}

	private void readFromParcel(final Parcel in) {
		in.readInt(); // Version

		session = in.readInt();
		name = in.readString();
		averageAvailable = in.readFloat();
		talkingState = in.readInt();
		final boolean[] boolArr = new boolean[3];
		in.readBooleanArray(boolArr);
		isCurrent = boolArr[0];
		muted = boolArr[1];
		deafened = boolArr[2];
		channel = in.readParcelable(null);
	}
}
