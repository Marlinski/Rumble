/*
 * Copyright (C) 2014 Disrupted Systems
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

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
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
                 + COUNT     + " INTEGER, "
                 + LAST_SEEN + " INTEGER NOT NULL DEFAULT 1, "
                 + "UNIQUE( " + HASHTAG + " ) "
          + " );";


    public HashtagDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    private boolean getHashtags(DatabaseExecutor.ReadableQueryCallback callback) {
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Cursor read() {
                        return getHashtags();
                    }
                }, callback);
    }
    private Cursor getHashtags() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor           = database.query(TABLE_NAME, null, null, null, null, null, null);
        return cursor;
    }

    public long getHashtagCount(String hashtag) {
        Cursor cursor = null;
        long count = 0;

        try {
            SQLiteDatabase db = databaseHelper.getReadableDatabase();
            cursor = db.query(TABLE_NAME, new String[]{COUNT}, HASHTAG + " = '"+hashtag+"'", null, null, null, null, null);
            if (cursor != null && cursor.moveToFirst())
                count = cursor.getLong(cursor.getColumnIndexOrThrow(COUNT));
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return count;
    }

    public void deleteHashtag(String hashtag){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, HASHTAG + " = ?" , new String[] {hashtag});
    }

    public void deleteHashtag(long tagID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, ID_WHERE, new String[] {tagID + ""} );
    }

    public long insertHashtag(String hashtag){
        long hashtagCount = this.getHashtagCount(hashtag);
        ContentValues contentValues = new ContentValues();
        contentValues.put(HASHTAG, hashtag);
        contentValues.put(COUNT, hashtagCount+1);
        contentValues.put(LAST_SEEN, SystemClock.currentThreadTimeMillis());

        long res = -1;
        if (hashtagCount == 0) {
            res = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        } else {
            Cursor cursor = null;
            try {
                databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, HASHTAG + " = '" + hashtag + "'", null);
                cursor = databaseHelper.getReadableDatabase().query(TABLE_NAME, new String[]{this.ID}, HASHTAG + " = '" + hashtag + "'", null, null, null, null, null);
                if (cursor != null && cursor.moveToFirst())
                    res = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
            } finally {
                if(cursor != null)
                    cursor.close();
            }
        }

        if(res >= 0)
            EventBus.getDefault().post(new HashtagInsertedEvent(hashtag));

        return res;
    }

}
