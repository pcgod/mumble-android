package org.pcgod.mumbleclient.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbAdapter {
	private static class DatabaseHelper extends SQLiteOpenHelper {

		public DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, 1);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("create table server (" +
					"server_id integer primary key autoincrement," +
					"host text not null," +
					"port integer," +
					"username text," +
					"password text" +
					");");
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			Log.w("mumbleclient", "Database upgrade from " + oldVersion
					+ " to " + newVersion);
		}
	}

	public static final String DATABASE_NAME = "mumble.db";
	public static final String SERVER_TABLE = "server";
	public static final String SERVER_COL_ID = "server_id";
	public static final String SERVER_COL_HOST = "host";
	public static final String SERVER_COL_PORT = "port";
	public static final String SERVER_COL_USERNAME = "username";
	public static final String SERVER_COL_PASSWORD = "password";
	
	private Context context;
	private SQLiteDatabase db;
	private DatabaseHelper dbHelper;

	public DbAdapter(Context context_) {
		context = context_;
	}

	public void close() {
		dbHelper.close();
	}

	public long createServer(String host, int port, String username, String password) {
		ContentValues values = new ContentValues();
		values.put(SERVER_COL_HOST, host);
		values.put(SERVER_COL_PORT, port);
		values.put(SERVER_COL_USERNAME, username);
		values.put(SERVER_COL_PASSWORD, password);
		return db.insert(SERVER_TABLE, null, values);
	}

	public boolean deleteServer(long serverId) {
		return db.delete(SERVER_TABLE, SERVER_COL_ID + " = " + serverId, null) > 0;
	}

	public Cursor fetchAllServers() {
		Cursor c = db.query(SERVER_TABLE, new String[] { SERVER_COL_ID,
				SERVER_COL_HOST, SERVER_COL_PORT, SERVER_COL_USERNAME,
				SERVER_COL_PASSWORD }, null, null, null, null, null);

		return c;
	}

	public Cursor fetchServer(long serverId) {
		Cursor c = db.query(SERVER_TABLE, new String[] { SERVER_COL_ID,
				SERVER_COL_HOST, SERVER_COL_PORT, SERVER_COL_USERNAME,
				SERVER_COL_PASSWORD }, SERVER_COL_ID + " = " + serverId, null,
				null, null, null);
		if (c != null)
			c.moveToFirst();

		return c;
	}
	
	public DbAdapter open() {
		dbHelper = new DatabaseHelper(context);
		db = dbHelper.getWritableDatabase();
		return this;
	}
}
