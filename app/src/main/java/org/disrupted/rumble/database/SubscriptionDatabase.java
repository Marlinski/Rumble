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

import java.util.ArrayList;

/**
 * @author Marlinski
 */
public class SubscriptionDatabase extends Database {

    private static final String TAG = "SubscriptionDatabase";

    public  static final String TABLE_NAME = "subscription";
    public  static final String CID = "_id";
    public  static final String HID = "_hid";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" +  CID + " INTEGER, "
                 + HID + " INTEGER, "
                 + " UNIQUE( " + HID + " , " + CID + "), "
                 + " FOREIGN KEY ( "+ CID + " ) REFERENCES " + ContactDatabase.TABLE_NAME   + " ( " + ContactDatabase.ID   + " ), "
                 + " FOREIGN KEY ( "+ HID + " ) REFERENCES " + HashtagDatabase.TABLE_NAME + " ( " + HashtagDatabase.ID + " )"
                 + " );";

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS hashtag_contact_id_index ON " + TABLE_NAME + " (" + CID + ");"
    };

    public static abstract class SubscriptionsQueryCallback implements DatabaseExecutor.ReadableQueryCallback {
        public final void onReadableQueryFinished(Object object) {
            if(object instanceof ArrayList)
                onSubscriptionsQueryFinished((ArrayList<String>)(object));
        }
        public abstract void onSubscriptionsQueryFinished(ArrayList<String> subscriptions);
    }

    public SubscriptionDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public boolean getLocalUserSubscriptions(SubscriptionsQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<String> read() {
                        return getLocalUserSubscriptions();
                    }
                }, callback);
    }
    private ArrayList<String> getLocalUserSubscriptions() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        StringBuilder query = new StringBuilder(
                "SELECT "+ HashtagDatabase.HASHTAG +" FROM "+HashtagDatabase.TABLE_NAME+" h"+
                " JOIN " + SubscriptionDatabase.TABLE_NAME+" s"+
                " ON h."+HashtagDatabase.ID+" = s."+SubscriptionDatabase.HID  +
                " JOIN " + ContactDatabase.TABLE_NAME+" c"                     +
                " ON s."+SubscriptionDatabase.CID+" = c."+ContactDatabase.ID +
                " WHERE c."+ContactDatabase.LOCALUSER+" = 1");
        query.append(" GROUP BY h."+HashtagDatabase.ID);
        Cursor cursor = database.rawQuery(query.toString(), null);
        if(cursor == null)
            return null;
        ArrayList<String> ret = new ArrayList<String>();
        try {
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursor.getString(cursor.getColumnIndexOrThrow(HashtagDatabase.HASHTAG)));
            }
        }finally {
            cursor.close();
        }
        return ret;
    }

    public Cursor getContactSubscriptions(String uid) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, CID+"=?", new String[] {uid}, null, null, null);

        return cursor;
    }

    public void deleteSubscription(long contactID, long hashtagID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, HID + " = " + String.valueOf(hashtagID) + " and " + CID + " = " + String.valueOf(contactID) , null);
    }

    public void deleteEntriesMatchingContactID(long contactID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, CID + " = ?" , new String[] {contactID + ""});
    }

    public long insertSubscription(long contactID, long hashtagID){
        ContentValues contentValues = new ContentValues();
        contentValues.put(CID, contactID);
        contentValues.put(HID, hashtagID);
        return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }


    public boolean subscribeLocalUser(final String hashtag, DatabaseExecutor.WritableQueryCallback callback) {
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (subscribeLocalUser(hashtag) > 0);
                    }
                }, callback);
    }
    public long subscribeLocalUser(String hashtag) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(ContactDatabase.TABLE_NAME, new String[] {ContactDatabase.ID}, ContactDatabase.LOCALUSER+" = 1",null,null,null,null);
        long ret = 0;
        if(cursor.moveToFirst()) {
            int localUserID = cursor.getInt(cursor.getColumnIndexOrThrow(ContactDatabase.ID));

            cursor.close();
            cursor = db.query(HashtagDatabase.TABLE_NAME, new String[]{HashtagDatabase.ID}, "lower("+HashtagDatabase.HASHTAG + ") = lower(?)", new String[]{hashtag}, null, null, null);
            if (cursor.moveToFirst()) {
                int hashtagID = cursor.getInt(cursor.getColumnIndexOrThrow(HashtagDatabase.ID));
                ContentValues contentValues = new ContentValues();
                contentValues.put(CID, localUserID);
                contentValues.put(HID, hashtagID);
                db = databaseHelper.getWritableDatabase();
                ret = db.insert(SubscriptionDatabase.TABLE_NAME, null, contentValues);
            }
        }
        cursor.close();
        return ret;
    }

    public boolean unsubscribeLocalUser(final String hashtag, DatabaseExecutor.WritableQueryCallback callback) {
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (unsubscribeLocalUser(hashtag) > 0);
                    }
                }, callback);
    }
    public long unsubscribeLocalUser(String hashtag) {
        SQLiteDatabase db = databaseHelper.getReadableDatabase();
        Cursor cursor = db.query(ContactDatabase.TABLE_NAME, new String[] {ContactDatabase.ID}, ContactDatabase.LOCALUSER+" = 1",null,null,null,null);
        long ret = 0;
        if(cursor.moveToFirst()) {
            int localUserID = cursor.getInt(cursor.getColumnIndexOrThrow(ContactDatabase.ID));

            cursor.close();
            cursor = db.query(HashtagDatabase.TABLE_NAME, new String[]{HashtagDatabase.ID}, "lower("+HashtagDatabase.HASHTAG + ") = lower(?)", new String[]{hashtag}, null, null, null);
            if (cursor.moveToFirst()) {
                int hashtagID = cursor.getInt(cursor.getColumnIndexOrThrow(HashtagDatabase.ID));
                db = databaseHelper.getWritableDatabase();
                ret = db.delete(SubscriptionDatabase.TABLE_NAME, HID + " = ? and " + CID + " = ?", new String[]{String.valueOf(hashtagID), String.valueOf(localUserID)});
            }
        }
        cursor.close();
        return ret;
    }
}
