package org.pcgod.mumbleclient.app;

import java.util.ArrayList;

import org.pcgod.mumbleclient.Channel;
import org.pcgod.mumbleclient.MumbleClient;
import org.pcgod.mumbleclient.R;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class ChannelList extends ListActivity {
	public class ChannelAdapter extends BaseAdapter {
		private Context ctx;
		private ArrayList<Channel> al;

		public ChannelAdapter(final Context context, final ArrayList<Channel> channelArray) {
			ctx = context;
			al = channelArray;
		}

		@Override
		public final int getCount() {
			return al.size();
		}

		@Override
		public final Object getItem(final int position) {
			return getItemId(position);
		}

		@Override
		public final long getItemId(final int position) {
			return al.get(position).id;
		}

		@Override
		public final View getView(final int position, final View v, final ViewGroup parent) {
			final Channel c = al.get(position);
			final TextView tv = new TextView(ctx);
			tv.setText(c.name + " (" + c.userCount + ")");
			tv.setTextSize(20);
			if (c.id == ServerList.client.currentChannel) {
				tv.setTypeface(Typeface.DEFAULT_BOLD);
			}
			return tv;
		}
	}

	public class ChannelBroadcastReceiver extends BroadcastReceiver {
		@Override
		public final void onReceive(final Context ctx, final Intent i) {
			updateList();
		}
	}

	private static final int ACTIVITY_USER_LIST = 0;

	private ChannelBroadcastReceiver bcReceiver;

	public final void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);

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

	private void updateList() {
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
	}

	protected final void onListItemClick(final ListView l, final View v, final int position, final long id) {
		super.onListItemClick(l, v, position, id);

		final Intent i = new Intent(this, UserList.class);
		i.putExtra("channelId", id);
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
}
