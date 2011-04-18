package org.pcgod.mumbleclient.app;

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
					   + "`password` TEXT, "
					   + "`keystore_file` TEXT,"
					   + "`keystore_password` TEXT"
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
						   + "`_id`, '', `host`, `port`, `username`, `password`, '', '' "
						   + "FROM `server_old`");
				db.execSQL("DROP TABLE `server_old`");
			} else if (oldVersion == 2	) {
				db.execSQL("ALTER TABLE `server` RENAME TO `server_old`");
				onCreate(db);
				db.execSQL("INSERT INTO `server` SELECT "
						   + "`_id`, `name`, `host`, `port`, `username`, `password`, '', '' "
						   + "FROM `server_old`");
				db.execSQL("DROP TABLE `server_old`");
			}
		}
	}

	public static final String DATABASE_NAME = "mumble.db";
	public static final String SERVER_TABLE = "server";
	public static final String SERVER_COL_ID = "_id";
	public static final String SERVER_COL_NAME = "name";
	public static final String SERVER_COL_HOST = "host";
	public static final String SERVER_COL_PORT = "port";
	public static final String SERVER_COL_USERNAME = "username";
	public static final String SERVER_COL_PASSWORD = "password";
	public static final String SERVER_COL_KEYSTORE_FILE = "keystore_file";
	public static final String SERVER_COL_KEYSTORE_PASSWORD = "keystore_password";

	private final Context context;
	private SQLiteDatabase db;
	private DatabaseHelper dbHelper;

	public DbAdapter(final Context context_) {
		context = context_;
	}

	public final void close() {
		dbHelper.close();
	}

	public final long createServer(
		final String name,
		final String host,
		final int port,
		final String username,
		final String password,
		final String keystoreFile,
		final String keystorePassword) {
		final ContentValues values = new ContentValues();
		values.put(SERVER_COL_NAME, name);
		values.put(SERVER_COL_HOST, host);
		values.put(SERVER_COL_PORT, port);
		values.put(SERVER_COL_USERNAME, username);
		values.put(SERVER_COL_PASSWORD, password);
		values.put(SERVER_COL_KEYSTORE_FILE, keystoreFile);
		values.put(SERVER_COL_KEYSTORE_PASSWORD, keystorePassword);
		return db.insert(SERVER_TABLE, null, values);
	}

	public final boolean deleteServer(final long serverId) {
		return db.delete(SERVER_TABLE, SERVER_COL_ID + " = " + serverId, null) > 0;
	}

	public final Cursor fetchAllServers() {
		final Cursor c = db.query(
			SERVER_TABLE,
			new String[] { SERVER_COL_ID, SERVER_COL_NAME, SERVER_COL_HOST,
					SERVER_COL_PORT, SERVER_COL_USERNAME, SERVER_COL_PASSWORD, 
					SERVER_COL_KEYSTORE_FILE, SERVER_COL_KEYSTORE_PASSWORD },
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
					SERVER_COL_PORT, SERVER_COL_USERNAME, SERVER_COL_PASSWORD, 
					SERVER_COL_KEYSTORE_FILE, SERVER_COL_KEYSTORE_PASSWORD },
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

	public final void updateServer(
		final long id,
		final String name,
		final String host,
		final int port,
		final String username,
		final String password,
		final String keystoreFile,
		final String keystorePassword) {
		final ContentValues values = new ContentValues();
		values.put(SERVER_COL_NAME, name);
		values.put(SERVER_COL_HOST, host);
		values.put(SERVER_COL_PORT, port);
		values.put(SERVER_COL_USERNAME, username);
		values.put(SERVER_COL_PASSWORD, password);
		values.put(SERVER_COL_KEYSTORE_FILE, keystoreFile);
		values.put(SERVER_COL_KEYSTORE_PASSWORD, keystorePassword);
		db.update(
			SERVER_TABLE,
			values,
			SERVER_COL_ID + "=?",
			new String[] { Long.toString(id) });
	}
}
