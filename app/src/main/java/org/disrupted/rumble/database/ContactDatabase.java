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

import org.disrupted.rumble.contact.Contact;
import org.disrupted.rumble.message.StatusMessage;

/**
 * @author Marlinski
 */
public class ContactDatabase extends Database  {

    private static final String TAG = "StatusDatabase";


    public static final String TABLE_NAME   = "contact";
    public static final String ID           = "_id";
    public static final String UID          = "uid";
    public static final String NAME         = "name";
    public static final String AVATAR       = "avatar";
    public static final String LOCALUSER    = "local";
    public static final String BTID         = "bluetoothID";
    public static final String WIFIID       = "wifiID";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID        + " INTEGER PRIMARY KEY, "
                 + UID       + " TEXT, "
                 + NAME      + " TEXT, "
                 + AVATAR    + " TEXT, "
                 + LOCALUSER + " INTEGER, "
                 + BTID      + " TEXT, "
                 + WIFIID    + " TEXT "
                 + " );";

    public ContactDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }


    public boolean getContacts(DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Cursor read() {
                        return getContacts();
                    }
                }, callback);
    }
    private Cursor getContacts() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        return cursor;
    }


    public Contact getLocalContact() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, LOCALUSER+" = 1", null, null, null, null);
        cursor.moveToFirst();

        String author = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
        String uid    = cursor.getString(cursor.getColumnIndexOrThrow(UID));
        boolean local = (cursor.getInt(cursor.getColumnIndexOrThrow(LOCALUSER)) == 1);

        return new Contact(author, uid, local);
    }


    public boolean getContact(final String uid, DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Cursor read() {
                        return getContact(uid);
                    }
                }, callback);
    }
    private Cursor getContact(String id) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, ID+" = ?", new String[] {id}, null, null, null);
        return cursor;
    }


    public boolean insertContact(final Contact contact, final DatabaseExecutor.WritableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (insertContact(contact) >= 0);
                    }
                }, callback);
    }
    private long insertContact(Contact contact){
        ContentValues contentValues = new ContentValues();
        contentValues.put(UID, contact.getUid());
        contentValues.put(NAME, contact.getName());
        contentValues.put(AVATAR, contact.getAvatar());
        if(contact.isLocal())
            contentValues.put(LOCALUSER, 1);
        else
            contentValues.put(LOCALUSER, 0);

        long contactID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);

        this.notifyContactListListener(contact);

        return contactID;
    }

}


