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
import android.util.Log;

import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.events.ContactUpdatedEvent;
import org.disrupted.rumble.database.objects.Contact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class ContactDatabase extends Database  {

    private static final String TAG = "ContactDatabase";

    public static final String TABLE_NAME   = "contact";
    public static final String ID           = "_id";
    public static final String UID          = "uid";
    public static final String NAME         = "name";
    public static final String AVATAR       = "avatar";
    public static final String LOCALUSER    = "local";
    public static final String TRUSTNESS    = "trustness";
    public static final String PUBKEY       = "pubkey";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID        + " INTEGER PRIMARY KEY, "
                 + UID       + " TEXT, "
                 + NAME      + " TEXT, "
                 + AVATAR    + " TEXT, "
                 + LOCALUSER + " INTEGER, "
                 + TRUSTNESS + " INTEGER, "
                 + PUBKEY    + " TEXT, "
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

    public Contact getContact(long contact_dbid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, null, ID+ " = ?", new String[] {Long.toString(contact_dbid)}, null, null, null);
            if(cursor == null)
                return null;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToContact(cursor);
            else
                return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public Contact getContact(String uid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, null, UID+ " = ?", new String[] {uid }, null, null, null);
            if(cursor == null)
                return null;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToContact(cursor);
            else
                return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public long getContactDBID(String contact_id) {
        long ret = -1;
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, new String[] {ID}, UID+ " = ?", new String[] {contact_id}, null, null, null);
            if(cursor == null)
                return ret;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
            else
                return -1;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public long insertOrUpdateContact(Contact contact){
        ContentValues contentValues = new ContentValues();
        contentValues.put(UID, contact.getUid());
        contentValues.put(NAME, contact.getName());
        contentValues.put(AVATAR, contact.getAvatar());
        contentValues.put(LOCALUSER, contact.isLocal() ? 1 : 0);

        long contactDBID = getContactDBID(contact.getUid());

        if(contactDBID < 0) {
            contactDBID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
            Log.d(TAG, "new contact inserted: " + contact.toString());
            EventBus.getDefault().post(new ContactInsertedEvent(contact));
        } else {
            databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, UID + " = ?", new String[]{contact.getUid()});
            Log.d(TAG, "contact updated: " + contact.toString());
            EventBus.getDefault().post(new ContactUpdatedEvent(contact));
        }

        return contactDBID;
    }

    private Contact cursorToContact(final Cursor cursor) {
        long contactDBID = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        String author    = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
        String uid       = cursor.getString(cursor.getColumnIndexOrThrow(UID));
        boolean local    = (cursor.getInt(cursor.getColumnIndexOrThrow(LOCALUSER)) == 1);
        Contact contact  = new Contact(author, uid, local);
        contact.setHashtagInterests(getHashtagsOfInterest(contactDBID));
        contact.setJoinedGroupIDs(getJoinedGroupIDs(contactDBID));
        return contact;
    }

    private Map<String, Integer> getHashtagsOfInterest(long contactDBID) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            StringBuilder query = new StringBuilder(
                    "SELECT h." + HashtagDatabase.HASHTAG + ", i."+ContactHashTagInterestDatabase.INTEREST +
                            " FROM " + HashtagDatabase.TABLE_NAME + " h" +
                            " JOIN " + ContactHashTagInterestDatabase.TABLE_NAME + " i" +
                            " ON h." + HashtagDatabase.ID + " = i." + ContactHashTagInterestDatabase.HDBID +
                            " WHERE i." + ContactHashTagInterestDatabase.CDBID + " = ?;");
            cursor = database.rawQuery(query.toString(), new String[]{Long.toString(contactDBID)});
            Map<String, Integer> ret = new HashMap<String, Integer>();
            if (cursor != null) {
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    ret.put(cursor.getString(cursor.getColumnIndexOrThrow(HashtagDatabase.HASHTAG )),
                            cursor.getInt(cursor.getColumnIndexOrThrow(ContactHashTagInterestDatabase.INTEREST))
                    );
                }
            }
            return ret;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    private Set<String> getJoinedGroupIDs(long contactDBID) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            StringBuilder query = new StringBuilder(
                    "SELECT g." + GroupDatabase.GID +
                            " FROM " + GroupDatabase.TABLE_NAME + " g" +
                            " JOIN " + ContactJoinGroupDatabase.TABLE_NAME + " c" +
                            " ON g." + GroupDatabase.ID + " = c." + ContactJoinGroupDatabase.GDBID +
                            " WHERE c." + ContactJoinGroupDatabase.UDBID + " = ?");
            cursor = database.rawQuery(query.toString(), new String[]{Long.toString(contactDBID)});
            Set<String> ret = new HashSet<String>();
            if (cursor != null) {
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    ret.add(cursor.getString(cursor.getColumnIndexOrThrow(GroupDatabase.GID)));
                }
            }
            return ret;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public Set<Contact> getContactsUsingMacAddress(String macAddress) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            StringBuilder query = new StringBuilder(
                    "SELECT c.* FROM " + ContactDatabase.TABLE_NAME + " c" +
                            " JOIN " + ContactInterfaceDatabase.TABLE_NAME + " ci" +
                            " ON c." + ContactDatabase.ID + " = ci." + ContactInterfaceDatabase.CONTACT_DBID +
                            " JOIN " + InterfaceDatabase.TABLE_NAME + " i" +
                            " ON i." + InterfaceDatabase.ID + " = ci." + ContactInterfaceDatabase.INTERFACE_DBID +
                            " WHERE i." + InterfaceDatabase.MACADDRESS + " = ?");
            cursor = database.rawQuery(query.toString(), new String[]{macAddress});
            Set<Contact> ret = new HashSet<Contact>();
            if (cursor != null) {
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    ret.add(cursorToContact(cursor));
                }
            }
            return ret;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

}


