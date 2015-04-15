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

import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.objects.Contact;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

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

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID        + " INTEGER PRIMARY KEY, "
                 + UID       + " TEXT, "
                 + NAME      + " TEXT, "
                 + AVATAR    + " TEXT, "
                 + LOCALUSER + " INTEGER, "
                 + "UNIQUE( " + UID + " ) "
                 + " );";

    public ContactDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public Contact getLocalContact() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, LOCALUSER+" = 1", null, null, null, null);
        if(cursor == null)
            return null;
        try {
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToContact(cursor);
            else
                return null;
        } finally {
            cursor.close();
        }
    }

    public boolean getContacts(DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Object read() {
                        return getContacts();
                    }
                }, callback);
    }
    private ArrayList<Contact> getContacts() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        if(cursor == null)
            return null;
        ArrayList<Contact> ret = new ArrayList<Contact>();
        try {
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursorToContact(cursor));
            }
        } finally {
            cursor.close();
        }
        return ret;
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
        contentValues.put(LOCALUSER, contact.isLocal() ? 1 : 0);

        long contactID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);

        if(contactID > 0)
            EventBus.getDefault().post(new ContactInsertedEvent(contact));

        return contactID;
    }

    private Contact cursorToContact(final Cursor cursor) {
        String author = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
        String uid    = cursor.getString(cursor.getColumnIndexOrThrow(UID));
        boolean local = (cursor.getInt(cursor.getColumnIndexOrThrow(LOCALUSER)) == 1);
        return new Contact(author, uid, local);
    }

}


