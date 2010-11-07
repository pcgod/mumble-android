package org.pcgod.mumbleclient.app;

import org.pcgod.mumbleclient.service.model.AccessToken;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

class DbAdapter {
	private static class DatabaseHelper extends SQLiteOpenHelper {
		public DatabaseHelper(final Context context) {
			super(context, DATABASE_NAME, null, 3);
		}

		@Override
		public void onCreate(final SQLiteDatabase db) {
			db.execSQL("CREATE TABLE `server` ("
					   + "`_id` INTEGER PRIMARY KEY AUTOINCREMENT,"
					   + "`name` TEXT NOT NULL,"
					   + "`host` TEXT NOT NULL,"
					   + "`port` INTEGER,"
					   + "`username` TEXT NOT NULL,"
					   + "`password` TEXT"
					   + ");");

			db.execSQL("CREATE TABLE `accesstoken` ("
					   + "`_id` INTEGER PRIMARY KEY AUTOINCREMENT,"
					   + "`server_id` INTEGER NOT NULL,"
					   + "`value` TEXT NOT NULL"
					   + ");");
		}

		@Override
		public void onUpgrade(
			final SQLiteDatabase db,
			final int oldVersion,
			final int newVersion) {
			Log.w("mumbleclient", "Database upgrade from " + oldVersion +
								  " to " + newVersion);
			if (oldVersion == 1) {
				db.execSQL("ALTER TABLE `server` RENAME TO `server_old`");
				onCreate(db);
				db.execSQL("INSERT INTO `server` SELECT "
						   + "`_id`, '', `host`, `port`, `username`, `password` "
						   + "FROM `server_old`");
				db.execSQL("DROP TABLE `server_old`");
			}

			if (oldVersion < 3) {
				db.execSQL("CREATE TABLE `accesstoken` ("
						   + "`_id` INTEGER PRIMARY KEY AUTOINCREMENT,"
						   + "`server_id` INTEGER NOT NULL,"
						   + "`value` TEXT NOT NULL"
						   + ");");
			}
		}
	}

	public static final String COL_ID = "_id";

	public static final String DATABASE_NAME = "mumble.db";
	public static final String SERVER_TABLE = "server";
	public static final String SERVER_COL_ID = COL_ID;
	public static final String SERVER_COL_NAME = "name";
	public static final String SERVER_COL_HOST = "host";
	public static final String SERVER_COL_PORT = "port";
	public static final String SERVER_COL_USERNAME = "username";
	public static final String SERVER_COL_PASSWORD = "password";

	public static final String ACCESS_TOKEN_TABLE = "accesstoken";
	public static final String ACCESS_TOKEN_COL_ID = COL_ID;
	public static final String ACCESS_TOKEN_COL_SERVER_ID = "server_id";
	public static final String ACCESS_TOKEN_COL_VALUE = "value";

	private final Context context;
	private SQLiteDatabase db;
	private DatabaseHelper dbHelper;

	public DbAdapter(final Context context_) {
		context = context_;
	}

	public final void close() {
		dbHelper.close();
	}

	public final AccessToken createAccessToken(
		final long serverId,
		final String value) {
		final ContentValues values = new ContentValues();
		values.put(ACCESS_TOKEN_COL_SERVER_ID, serverId);
		values.put(ACCESS_TOKEN_COL_VALUE, value);
		return new AccessToken(value, db.insert(
			ACCESS_TOKEN_TABLE,
			null,
			values), serverId);
	}

	public final long createServer(
		final String name,
		final String host,
		final int port,
		final String username,
		final String password) {
		final ContentValues values = new ContentValues();
		values.put(SERVER_COL_NAME, name);
		values.put(SERVER_COL_HOST, host);
		values.put(SERVER_COL_PORT, port);
		values.put(SERVER_COL_USERNAME, username);
		values.put(SERVER_COL_PASSWORD, password);
		return db.insert(SERVER_TABLE, null, values);
	}

	public final boolean deleteAccessToken(final long accessTokenId) {
		return db.delete(ACCESS_TOKEN_TABLE, ACCESS_TOKEN_COL_ID + " = " +
											 accessTokenId, null) > 0;
	}

	public final boolean deleteAccessTokenByServerId(final long serverId) {
		return db.delete(ACCESS_TOKEN_TABLE, ACCESS_TOKEN_COL_SERVER_ID +
											 " = " + serverId, null) > 0;
	}

	public final boolean deleteServer(final long serverId) {
		return db.delete(SERVER_TABLE, SERVER_COL_ID + " = " + serverId, null) > 0 &&
			   this.deleteAccessTokenByServerId(serverId);
	}

	// FIXME: Return a cursor here
	public final AccessToken[] fetchAccessTokenByServerId(final long serverId) {
		final Cursor c = db.query(
			ACCESS_TOKEN_TABLE,
			new String[] { ACCESS_TOKEN_COL_ID, ACCESS_TOKEN_COL_VALUE },
			ACCESS_TOKEN_COL_SERVER_ID + "=?",
			new String[] { Long.toString(serverId) },
			null,
			null,
			null);

		if (c.moveToFirst()) {
			final AccessToken[] result = new AccessToken[c.getCount()];
			for (int i = 0; i < result.length; i++) {
				final long id = c.getLong(c.getColumnIndexOrThrow(ACCESS_TOKEN_COL_ID));
				final String value = c.getString(c.getColumnIndexOrThrow(ACCESS_TOKEN_COL_VALUE));

				result[i] = new AccessToken(value, id, serverId);
				c.moveToNext();
			}
			c.close();
			return result;
		} else {
			return new AccessToken[0];
		}
	}

	// FIXME: Return a cursor here
	public final AccessToken[] fetchAllAccessToken() {
		final Cursor c = db.query(
			ACCESS_TOKEN_TABLE,
			new String[] { ACCESS_TOKEN_COL_ID, ACCESS_TOKEN_COL_SERVER_ID,
					ACCESS_TOKEN_COL_VALUE },
			null,
			null,
			null,
			null,
			null);

		if (c.moveToFirst()) {
			final AccessToken[] result = new AccessToken[c.getCount()];
			for (int i = 0; i < result.length; i++) {
				final String value = c.getString(c.getColumnIndexOrThrow(ACCESS_TOKEN_COL_VALUE));
				final long id = c.getLong(c.getColumnIndexOrThrow(ACCESS_TOKEN_COL_ID));
				final long serverId = c.getLong(c.getColumnIndexOrThrow(ACCESS_TOKEN_COL_SERVER_ID));

				result[i] = new AccessToken(value, id, serverId);
				c.moveToNext();
			}
			c.close();
			return result;
		} else {
			return new AccessToken[0];
		}
	}

	public final Cursor fetchAllServers() {
		final Cursor c = db.query(
			SERVER_TABLE,
			new String[] { SERVER_COL_ID, SERVER_COL_NAME, SERVER_COL_HOST,
					SERVER_COL_PORT, SERVER_COL_USERNAME, SERVER_COL_PASSWORD },
			null,
			null,
			null,
			null,
			null);

		return c;
	}

	public final Cursor fetchServer(final long serverId) {
		final Cursor c = db.query(
			SERVER_TABLE,
			new String[] { SERVER_COL_ID, SERVER_COL_NAME, SERVER_COL_HOST,
					SERVER_COL_PORT, SERVER_COL_USERNAME, SERVER_COL_PASSWORD },
			SERVER_COL_ID + " = " + serverId,
			null,
			null,
			null,
			null);
		if (c != null) {
			c.moveToFirst();
		}

		return c;
	}

	public final DbAdapter open() {
		dbHelper = new DatabaseHelper(context);
		db = dbHelper.getWritableDatabase();
		return this;
	}

	public final void updateAccessToken(
		final long accessTokenId,
		final long serverId,
		final String value) {
		final ContentValues values = new ContentValues();
		values.put(ACCESS_TOKEN_COL_SERVER_ID, serverId);
		values.put(ACCESS_TOKEN_COL_VALUE, value);
		db.update(
			ACCESS_TOKEN_TABLE,
			values,
			ACCESS_TOKEN_COL_ID + "=?",
			new String[] { Long.toString(accessTokenId) });
	}

	public final void updateServer(
		final long id,
		final String name,
		final String host,
		final int port,
		final String username,
		final String password) {
		final ContentValues values = new ContentValues();
		values.put(SERVER_COL_NAME, name);
		values.put(SERVER_COL_HOST, host);
		values.put(SERVER_COL_PORT, port);
		values.put(SERVER_COL_USERNAME, username);
		values.put(SERVER_COL_PASSWORD, password);
		db.update(
			SERVER_TABLE,
			values,
			SERVER_COL_ID + "=?",
			new String[] { Long.toString(id) });
	}
}
