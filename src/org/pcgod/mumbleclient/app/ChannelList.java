package org.pcgod.mumbleclient.app;

import java.util.ArrayList;

import org.pcgod.mumbleclient.Channel;
import org.pcgod.mumbleclient.R;

import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
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
			TextView tv = new TextView(ctx);
			tv.setText(al.get(position).name);
			return tv;
		}
	}

	public class ChannelBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context ctx, Intent i) {
			updateList();
		}
	}

	private ChannelBroadcastReceiver bcReceiver;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.channel_list);

		findViewById(R.id.Button01).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				updateList();
				ServerList.client.printChanneList();
			}
		});

		findViewById(R.id.Button02).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				ServerList.client.printUserList();
			}
		});

		setListAdapter(new ChannelAdapter(this, ServerList.client.channelArray));
		updateList();
	}

	private void updateList() {
		((BaseAdapter) getListAdapter()).notifyDataSetChanged();
	}

	@Override
	protected void onPause() {
		super.onPause();

		unregisterReceiver(bcReceiver);
	}

	@Override
	protected void onResume() {
		super.onResume();

		IntentFilter ifilter = new IntentFilter(
				"mumbleclient.intent.CHANNEL_LIST_UPDATE");
		bcReceiver = new ChannelBroadcastReceiver();
		registerReceiver(bcReceiver, ifilter);
	}
}
