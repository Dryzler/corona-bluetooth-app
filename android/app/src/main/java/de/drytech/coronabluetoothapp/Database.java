package de.drytech.coronabluetoothapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {
    private static Database database = null;

    public static Database get() {
        return database;
    }

    public static void initialize(Context context) {
        if (database == null) {
            database = new Database(context);
        }
    }

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "CoronaBluetoothApp.db";

    private Database(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);


    }

    public Cursor query(String sql) {
        return getReadableDatabase().rawQuery(sql, null);
    }

    public void execute(String sql) {
        getWritableDatabase().execSQL(sql);
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE devices (device TEXT PRIMARY KEY)");
    }

    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}