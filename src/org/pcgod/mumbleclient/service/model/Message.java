package org.pcgod.mumbleclient.service.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Message implements Parcelable {
	public static final int DIRECTION_SENT = 0;
	public static final int DIRECTION_RECEIVED = 1;

	public static final Parcelable.Creator<Message> CREATOR = new Creator<Message>() {
		@Override
		public Message createFromParcel(final Parcel source) {
			return new Message(source);
		}

		@Override
		public Message[] newArray(final int size) {
			return new Message[size];
		}
	};

	public String message;
	public String sender;
	public User actor;
	public Channel channel;
	public long timestamp;
	public int channelIds;
	public int treeIds;

	public int direction;

	public Message() {
	}

	public Message(final Parcel parcel) {
		readFromParcel(parcel);
	}

	@Override
	public int describeContents() {
		return 0;
	}

	public void readFromParcel(final Parcel in) {
		in.readInt(); // Version

		message = in.readString();
		sender = in.readString();
		actor = in.readParcelable(null);
		channel = in.readParcelable(null);
		timestamp = in.readLong();
		channelIds = in.readInt();
		treeIds = in.readInt();
		direction = in.readInt();
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(0); // Version

		dest.writeString(message);
		dest.writeString(sender);
		dest.writeParcelable(actor, 0);
		dest.writeParcelable(channel, 0);
		dest.writeLong(timestamp);
		dest.writeInt(channelIds);
		dest.writeInt(treeIds);
		dest.writeInt(direction);
	}
}
