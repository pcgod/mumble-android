package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.MumbleClient;
import org.pcgod.mumbleclient.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class ServerList extends ListActivity {
	public class ServerAdapter extends BaseAdapter {
		private Context context;
		private Cursor cursor;

		public ServerAdapter(Context context_, DbAdapter dbAdapter_) {
			context = context_;
			cursor = dbAdapter_.fetchAllServers();
			startManagingCursor(cursor);
		}

		@Override
		public int getCount() {
			return cursor.getCount();
		}

		@Override
		public Object getItem(int position) {
			return getItemId(position);
		}

		@Override
		public long getItemId(int position) {
			cursor.moveToPosition(position);
			return cursor.getLong(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_ID));
		}

		@Override
		public View getView(int position, View v, ViewGroup parent) {
			LayoutInflater inflater = (LayoutInflater) context
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View row = inflater.inflate(R.layout.server_list_row, null);

			TextView nameText = (TextView) row
					.findViewById(R.id.server_row_name);
			TextView userText = (TextView) row
					.findViewById(R.id.server_row_user);

			cursor.moveToPosition(position);
			String serverHost = cursor.getString(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_HOST));
			int serverPort = cursor.getInt(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PORT));
			String serverUsername = cursor.getString(cursor
					.getColumnIndexOrThrow(DbAdapter.SERVER_COL_USERNAME));

			nameText.setText(serverHost + ":" + serverPort);
			userText.setText(serverUsername);

			return row;
		}
	}

	private Thread clientThread;
	private DbAdapter dbAdapter;
	private long serverToDeleteId = -1;
	public static MumbleClient client;

	private static final int ACTIVITY_ADD_SERVER = 0;
	private static final int ACTIVITY_CHANNEL_LIST = 1;
	private static final int DIALOG_DELETE_SERVER = 0;
	private static final int MENU_ADD_SERVER = Menu.FIRST;
	private static final int MENU_EDIT_SERVER = Menu.FIRST + 1;
	private static final int MENU_DELETE_SERVER = Menu.FIRST + 2;

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		switch (item.getItemId()) {
		case MENU_EDIT_SERVER:
			return true;
		case MENU_DELETE_SERVER:
			serverToDeleteId = info.id;
			showDialog(DIALOG_DELETE_SERVER);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		System.loadLibrary("celt_interface");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		registerForContextMenu(getListView());

		dbAdapter = new DbAdapter(this);
		dbAdapter.open();

		fillList();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_EDIT_SERVER, 1, "Edit").setIcon(
				android.R.drawable.ic_menu_edit);
		menu.add(0, MENU_DELETE_SERVER, 1, "Delete").setIcon(
				android.R.drawable.ic_menu_delete);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_ADD_SERVER, 0, "Add Server").setIcon(
				android.R.drawable.ic_menu_add);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ADD_SERVER:
			addServer();
			return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}

	private void addServer() {
		Intent i = new Intent(this, ServerAdd.class);
		startActivityForResult(i, ACTIVITY_ADD_SERVER);
	}

	private Dialog createDeleteServerDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Are you sure you want to delete this server?")
				.setCancelable(false).setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								if (serverToDeleteId > 0) {
									dbAdapter.deleteServer(serverToDeleteId);
									serverToDeleteId = -1;
									fillList();
								}
							}
						}).setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								dialog.cancel();
							}
						});

		return builder.create();
	}

	private void fillList() {
		setListAdapter(new ServerAdapter(this, dbAdapter));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		fillList();
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		Dialog d;
		switch (id) {
		case DIALOG_DELETE_SERVER:
			d = createDeleteServerDialog();
			break;
		default:
			d = null;
		}
		return d;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		Intent i = new Intent(this, ChannelList.class);
		startActivityForResult(i, ACTIVITY_CHANNEL_LIST);

		Cursor c = dbAdapter.fetchServer(id);
		String host = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_HOST));
		int port = c.getInt(c.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PORT));
		String username = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_USERNAME));
		String password = c.getString(c
				.getColumnIndexOrThrow(DbAdapter.SERVER_COL_PASSWORD));

		if (clientThread != null) {
			clientThread.interrupt();
		}

		client = new MumbleClient(this, host, port, username, password);
		clientThread = new Thread(client, "net");
		clientThread.start();
	}
}
