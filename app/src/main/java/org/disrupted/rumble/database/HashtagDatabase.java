/*
 * Copyright (C) 2014 Lucien Loiseau
 *
 * This file is part of Rumble.
 *
 * Rumble is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Rumble is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;

import org.disrupted.rumble.database.events.HashtagInsertedEvent;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class HashtagDatabase extends  Database{

    private static final String TAG = "HashTagDatabase";


    public  static final String TABLE_NAME    = "hashtags";
    public  static final String ID            = "_id";
    public  static final String HASHTAG       = "hashtag";
    public  static final String COUNT         = "count";
    public  static final String LAST_SEEN     = "last";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID        + " INTEGER PRIMARY KEY, "
                 + HASHTAG   + " TEXT, "
                 + "UNIQUE( " + HASHTAG + " ) "
          + " );";


    public HashtagDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public boolean getHashtags(DatabaseExecutor.ReadableQueryCallback callback) {
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<String> read() {
                        return getHashtags();
                    }
                }, callback);
    }
    private ArrayList<String>  getHashtags() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor           = database.query(TABLE_NAME, null, null, null, null, null, null);
        if(cursor == null)
            return null;
        ArrayList<String> ret = new ArrayList<String>();
        try {
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursor.getString(cursor.getColumnIndexOrThrow(HASHTAG)));
            }
        }finally {
            cursor.close();
        }
        return ret;
    }

    public long getHashtagDBID(String hashtag) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = databaseHelper.getReadableDatabase();
            cursor = db.query(TABLE_NAME, new String[]{ID}, HASHTAG + " = ?", new String[]{hashtag.toLowerCase()}, null, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isAfterLast())
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return -1;
    }

    public long insertHashtag(String hashtag){
        long rowid = getHashtagDBID(hashtag);
        if(rowid < 0) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(HASHTAG, hashtag.toLowerCase());
            rowid = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
            EventBus.getDefault().post(new HashtagInsertedEvent(hashtag));
        }

        return rowid;
    }

}
