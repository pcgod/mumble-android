package org.pcgod.mumbleclient.service.model;

import android.os.Parcel;
import android.os.Parcelable;

public class Channel implements Parcelable {
	public static final Parcelable.Creator<Channel> CREATOR = new Creator<Channel>() {
		@Override
		public Channel createFromParcel(final Parcel source) {
			return new Channel(source);
		}

		@Override
		public Channel[] newArray(final int size) {
			return new Channel[size];
		}
	};

	public int id;
	public String name;
	public int userCount;
    public boolean hasParent;
    public boolean hasPosition;
    public int parent;
    public int position;

	/**
	 * Value signaling whether this channel has just been removed.
	 * Once this value is set the connection signals one last update for the
	 * channel which should result in the channel being removed from all the
	 * caches where it might be stored.
	 */
	public boolean removed = false;

	public Channel() {
	}

	public Channel(final Parcel parcel) {
		readFromParcel(parcel);
	}

	@Override
	public int describeContents() {
		// TODO Auto-generated method stub
		return 0;
	}

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
		return "Channel [id=" + id + ", name=" + name + ", userCount=" +
			   userCount + "]";
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		dest.writeInt(0); // Version

		dest.writeInt(id);
		dest.writeString(name);
		dest.writeInt(userCount);
        dest.writeInt(hasParent?1:0);
        dest.writeInt(hasPosition?1:0);
        dest.writeInt(parent);
        dest.writeInt(position);
	}

	private void readFromParcel(final Parcel in) {
		in.readInt(); // Version

		id = in.readInt();
		name = in.readString();
		userCount = in.readInt();
        hasParent = in.readInt()==1;
        hasPosition = in.readInt()==1;
        parent = in.readInt();
        position = in.readInt();
	}
}
