package org.pcgod.mumbleclient.app;

import java.util.List;

import org.pcgod.mumbleclient.service.model.Channel;
import org.pcgod.mumbleclient.service.MumbleClient;
import org.pcgod.mumbleclient.R;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ChannelList extends ListActivity {
	private class ChannelAdapter extends ArrayAdapter<Channel> {
		public ChannelAdapter(final Context context,
				final List<Channel> channels) {
			super(context, android.R.layout.simple_list_item_1, channels);
		}

		@Override
		public final View getView(final int position, View v,
				final ViewGroup parent) {
			if (v == null) {
				final LayoutInflater inflater = (LayoutInflater) ChannelList.this
						.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = inflater.inflate(android.R.layout.simple_list_item_1, null);
			}
			final Channel c = getItem(position);
			final TextView tv = (TextView) v.findViewById(android.R.id.text1);
			tv.setText(c.name + " (" + c.userCount + ")");
			if (c.id == ServerList.client.currentChannel) {
				tv.setTypeface(Typeface.DEFAULT_BOLD);
			} else {
				tv.setTypeface(Typeface.DEFAULT);
			}
			return tv;
		}
	}

	private class ChannelBroadcastReceiver extends BroadcastReceiver {
		@Override
		public final void onReceive(final Context ctx, final Intent i) {
			updateList();
		}
	}

	private static final int ACTIVITY_USER_LIST = 0;
	private static final int MENU_CHAT = Menu.FIRST;

	private ChannelBroadcastReceiver bcReceiver;

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		menu.add(0, MENU_CHAT, 0, "Chat").setIcon(
				android.R.drawable.ic_btn_speak_now);
		return true;
	}

	@Override
	public final boolean onMenuItemSelected(final int featureId,
			final MenuItem item) {
		switch (item.getItemId()) {
		case MENU_CHAT:
			final Intent i = new Intent(this, ChatActivity.class);
			startActivity(i);
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	@Override
	protected final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);
		setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

		if (ServerList.client == null) {
			finish();
			return;
		}

		if (!ServerList.client.isConnected()) {
			finish();
			return;
		}

		setListAdapter(new ChannelAdapter(this, ServerList.client.channelArray));
		updateList();
	}

	@Override
	protected final void onListItemClick(final ListView l, final View v,
			final int position, final long id) {
		super.onListItemClick(l, v, position, id);

		final Channel c = (Channel) getListView().getItemAtPosition(position);
		final Intent i = new Intent(this, UserList.class);
		i.putExtra("channelId", (long) c.id);
		startActivityForResult(i, ACTIVITY_USER_LIST);
	}

	@Override
	protected final void onPause() {
		super.onPause();

		unregisterReceiver(bcReceiver);
	}

	@Override
	protected final void onResume() {
		super.onResume();

		updateList();
		final IntentFilter ifilter = new IntentFilter(
				MumbleClient.INTENT_CHANNEL_LIST_UPDATE);
		ifilter.addAction(MumbleClient.INTENT_USER_LIST_UPDATE);
		bcReceiver = new ChannelBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}

	void updateList() {
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
	}
}
