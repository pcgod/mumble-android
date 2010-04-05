package org.pcgod.mumbleclient.app;

import java.util.ArrayList;

import org.pcgod.mumbleclient.Channel;
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

		public ChannelAdapter(Context context, ArrayList<Channel> channelArray) {
			ctx = context;
			al = channelArray;
		}

		@Override
		public int getCount() {
			return al.size();
		}

		@Override
		public Object getItem(int position) {
			return getItemId(position);
		}

		@Override
		public long getItemId(int position) {
			return al.get(position).id;
		}

		@Override
		public View getView(int position, View v, ViewGroup parent) {
			Channel c = al.get(position);
			TextView tv = new TextView(ctx);
			tv.setText(c.name + " (" + c.userCount + ")");
			if (c.id == ServerList.client.currentChannel) {
				tv.setTypeface(Typeface.DEFAULT_BOLD);
			}
			return tv;
		}
	}

	public class ChannelBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context ctx, Intent i) {
			updateList();
		}
	}

	private static final int ACTIVITY_USER_LIST = 0;

	private ChannelBroadcastReceiver bcReceiver;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);

		if (ServerList.client == null)
			finish();

		if (!ServerList.client.isConnected())
			finish();
		
		setListAdapter(new ChannelAdapter(this, ServerList.client.channelArray));
		updateList();
	}

	private void updateList() {
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);

		Intent i = new Intent(this, UserList.class);
		i.putExtra("channelId", id);
		startActivityForResult(i, ACTIVITY_USER_LIST);
	}

	@Override
	protected void onPause() {
		super.onPause();

		unregisterReceiver(bcReceiver);
	}

	@Override
	protected void onResume() {
		super.onResume();

		updateList();
		IntentFilter ifilter = new IntentFilter(
				"mumbleclient.intent.CHANNEL_LIST_UPDATE");
		ifilter.addAction("mumbleclient.intent.USER_LIST_UPDATE");
		bcReceiver = new ChannelBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}
}
