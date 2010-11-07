package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.R;
import org.pcgod.mumbleclient.service.model.AccessToken;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class AccessTokens extends ConnectedListActivity {
	private static final int MENU_ADD_TOKEN = Menu.FIRST;
	private static final int MENU_DELETE_TOKEN = Menu.FIRST + 1;
	private static final int MENU_EDIT_TOKEN = Menu.FIRST + 2;

	private ArrayAdapter<AccessToken> adapter;
	private long serverID;
	private DbAdapter dbAdapter;

	@Override
	public boolean onContextItemSelected(final MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case MENU_DELETE_TOKEN:
			final AccessToken deleted = this.adapter.getItem(info.position);
			this.dbAdapter.open();
			this.dbAdapter.deleteAccessToken(deleted.id);
			this.dbAdapter.close();
			this.adapter.remove(deleted);
			return true;

		case MENU_EDIT_TOKEN:
			this.renameToken(this.adapter.getItem(info.position));
			// Show dialog to change name
			return true;

		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.access_token_list);

		this.adapter = new ArrayAdapter<AccessToken>(
			this,
			android.R.layout.simple_list_item_1);

		final ListView tokens = this.getListView();
		tokens.setAdapter(this.adapter);
		this.registerForContextMenu(tokens);

		this.dbAdapter = new DbAdapter(this);
		this.dbAdapter.open();
	}

	@Override
	public void onCreateContextMenu(
		final ContextMenu menu,
		final View v,
		final ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		menu.add(0, MENU_DELETE_TOKEN, 1, R.string.accessTokenDelete);
		menu.add(0, MENU_EDIT_TOKEN, 1, R.string.accessTokenEdit);
	}

	@Override
	public final boolean onCreateOptionsMenu(final Menu menu) {
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_ADD_TOKEN, 0, R.string.accessAddToken).setIcon(
			android.R.drawable.ic_menu_add);
		return true;
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
			// Send all tokens if there are changes
			final String[] tokens = new String[this.adapter.getCount()];
			for (int i = 0; i < tokens.length; i++) {
				tokens[i] = this.adapter.getItem(i).value;
			}
			this.mService.authenticate(tokens);

		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public final boolean onMenuItemSelected(
		final int featureId,
		final MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ADD_TOKEN:
			addToken();
			return true;
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	private void addToken() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle(R.string.accessAddToken);
		alert.setMessage("Input the name of the new access token.");

		final EditText inputEdit = new EditText(this);
		inputEdit.setHint(R.string.accessAccessToken);
		alert.setView(inputEdit);

		alert.setPositiveButton(
			android.R.string.ok,
			new DialogInterface.OnClickListener() {
				public void onClick(
					final DialogInterface dialog,
					final int whichButton) {
					AccessTokens.this.addToken(inputEdit.getText().toString());
				}
			});
		alert.setNegativeButton(android.R.string.cancel, null);

		alert.show();
	}

	private void addToken(String newToken) {
		newToken = newToken.trim();
		if (newToken.length() > 0) {
			this.adapter.add(this.dbAdapter.createAccessToken(
				this.serverID,
				newToken));
		} else {
			Toast.makeText(
				this,
				R.string.accessNothingToAdd,
				Toast.LENGTH_SHORT).show();
		}
	}

	private void renameToken(final AccessToken token) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);

		alert.setTitle("Rename access token");
		alert.setMessage("Input the new name of the access token.");

		final EditText inputEdit = new EditText(this);
		inputEdit.setText(token.value);
		inputEdit.setHint(R.string.accessAccessToken);
		alert.setView(inputEdit);

		alert.setPositiveButton(
			android.R.string.ok,
			new DialogInterface.OnClickListener() {
				public void onClick(
					final DialogInterface dialog,
					final int whichButton) {
					AccessTokens.this.renameToken(
						token,
						inputEdit.getText().toString());
				}
			});
		alert.setNegativeButton(android.R.string.cancel, null);

		alert.show();
	}

	private void renameToken(final AccessToken token, String newName) {
		newName = newName.trim();
		if (newName.equals(token.value)) {
			Toast.makeText(this, R.string.accessNoChanges, Toast.LENGTH_SHORT).show();
		} else if (newName.length() > 0) {
			token.value = newName;
			this.dbAdapter.open();
			this.dbAdapter.updateAccessToken(token.id, token.serverId, newName);
			this.dbAdapter.close();
		} else {
			Toast.makeText(
				this,
				R.string.accessNothingToChange,
				Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	protected final void onDestroy() {
		super.onDestroy();

		this.dbAdapter.close();
	}

	@Override
	protected void onServiceBound() {
		super.onServiceBound();

		this.serverID = this.mService.getServerId();
		// Access Tokens of an existing server or global access tokens
		if (this.serverID >= -1) {
			// load all access tokens of the serverID
			for (final AccessToken token : this.dbAdapter.fetchAccessTokenByServerId(serverID)) {
				this.adapter.add(token);
			}
		} else {
			// Invalid ID
		}
	}
}
