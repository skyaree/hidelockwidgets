package com.squeeare.hidelockwidgets;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

public class ConfigProvider extends ContentProvider {
    public static final String AUTH = "com.squeeare.hidelockwidgets.config";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SharedPreferences sp = getContext().getSharedPreferences("cfg", 0);
        MatrixCursor c = new MatrixCursor(new String[]{
                "enabled", "hide_clock", "mode", "widget_provider", "custom_text", "scale", "offset_y"
        });
        c.addRow(new Object[]{
                sp.getBoolean("enabled", true) ? 1 : 0,
                sp.getBoolean("hide_clock", true) ? 1 : 0,
                sp.getString("mode", "clock"),
                sp.getString("widget_provider", ""),
                sp.getString("custom_text", "TECNO LG8n"),
                sp.getFloat("scale", 1.0f),
                sp.getInt("offset_y", 0)
        });
        return c;
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/hidelockwidgets.config"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
