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
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.events.ContactUpdatedEvent;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Interface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class ContactDatabase extends Database  {

    private static final String TAG = "ContactDatabase";

    public static final String TABLE_NAME   = "contact";
    public static final String ID           = "_id";
    public static final String UID          = "uid";
    public static final String NAME         = "name";
    public static final String LAST_MET     = "last_met";
    public static final String NB_STATUS_SENT  = "nb_status_sent";
    public static final String NB_STATUS_RCVD  = "nb_status_received";
    public static final String AVATAR       = "avatar";
    public static final String LOCALUSER    = "local";
    public static final String TRUSTNESS    = "trustness";
    public static final String PUBKEY       = "pubkey";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID        + " INTEGER PRIMARY KEY, "
                 + UID       + " TEXT, "
                 + NAME      + " TEXT, "
                 + AVATAR    + " TEXT, "
                 + NB_STATUS_SENT  + " INTEGER, "
                 + NB_STATUS_RCVD  + " INTEGER, "
                 + LAST_MET  + " INTEGER, "
                 + LOCALUSER + " INTEGER, "
                 + TRUSTNESS + " INTEGER, "
                 + PUBKEY    + " TEXT, "
                 + "UNIQUE( " + UID + " ) "
                 + " );";

    public static class ContactQueryOption {
        public static final long FILTER_GROUP  = 0x0001;
        public static final long FILTER_MET    = 0x0002;
        public static final long FILTER_FRIEND = 0x0004;
        public static final long FILTER_LOCAL  = 0x0008;

        public enum ORDER_BY {
            NO_ORDERING,
            LAST_TIME_MET,
            NB_STATUS_SENT,
            NB_STATUS_RECEIVED
        }

        public long         filterFlags;

        public String   gid;
        public boolean  met;
        public boolean  friend;
        public boolean  local;
        public int      answerLimit;
        public ORDER_BY order_by;

        public ContactQueryOption() {
            filterFlags = 0x00;
            gid         = null;
            met = false;
            friend = false;
            local = false;
            answerLimit = 0;
            order_by = ORDER_BY.NO_ORDERING;
        }
    }

    // caching the localContact as it is accessed very often
    private Contact localContact;

    public ContactDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
        localContact = null;
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public Contact getLocalContact() {
        if(localContact != null)
            return localContact;
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, LOCALUSER+" = 1", null, null, null, null);
        if(cursor == null)
            return null;
        try {
            if(cursor.moveToFirst() && !cursor.isAfterLast()) {
                localContact = cursorToContact(cursor);
                return localContact;
            } else {
                localContact = null;
                return null;
            }
        } finally {
            cursor.close();
        }
    }

    public boolean getContacts(final ContactQueryOption options,DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Object read() {
                        return getContacts(options);
                    }
                }, callback);
    }
    private ArrayList<Contact> getContacts(ContactQueryOption options) {
        if(options == null)
            options = new ContactQueryOption();

        /* 1st:  configure what the query will return */
        String select = " c.* ";

        StringBuilder query = new StringBuilder(
                "SELECT "+select+" FROM "+ ContactDatabase.TABLE_NAME+" c"
        );

        boolean groupby = false;
        boolean firstwhere = true;
        List<String> argumentList = new ArrayList<String>();

        /* 2nd:  Join The tables as needed */
        boolean groupJoined = false;
        if (((options.filterFlags & ContactQueryOption.FILTER_GROUP) == ContactQueryOption.FILTER_GROUP) && (options.gid != null)) {
            query.append(
                    " JOIN " + ContactGroupDatabase.TABLE_NAME + " cg" +
                    " ON c." + ContactDatabase.ID + " = cg." + ContactGroupDatabase.UDBID +
                    " JOIN " + GroupDatabase.TABLE_NAME + " g" +
                    " ON g." + GroupDatabase.ID + " = cg." + ContactGroupDatabase.GDBID);
            groupJoined = true;
        }

        /* 3rd:  Add the constraints */
        if (options.filterFlags > 0)
            query.append(" WHERE ( ");

        if(groupJoined && ((options.filterFlags & ContactQueryOption.FILTER_GROUP) == ContactQueryOption.FILTER_GROUP) && (options.gid != null)) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" g."+GroupDatabase.GID +" = ? ");
            argumentList.add(options.gid);
            groupby = true;
        }
        if((options.filterFlags & ContactQueryOption.FILTER_MET) == ContactQueryOption.FILTER_MET) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            if(options.met)
                query.append(" c."+ContactDatabase.LAST_MET +" > 0 ");
            else
                query.append(" c."+ContactDatabase.LAST_MET +" = 0 ");
        }
        if((options.filterFlags & ContactQueryOption.FILTER_FRIEND) == ContactQueryOption.FILTER_FRIEND) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            if(options.friend)
                query.append(" c."+ContactDatabase.TRUSTNESS +" = 1 ");
            else
                query.append(" c."+ContactDatabase.TRUSTNESS +" = 0 ");
        }
        if((options.filterFlags & ContactQueryOption.FILTER_LOCAL) == ContactQueryOption.FILTER_LOCAL) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            if(options.local)
                query.append(" c."+ContactDatabase.LOCALUSER +" = 1 ");
            else
                query.append(" c."+ContactDatabase.LOCALUSER +" = 0 ");
        }
        if (options.filterFlags > 0)
            query.append(" ) ");

        /* 4th: group by if necessary */
        if (groupby)
            query.append(" GROUP BY c." + ContactDatabase.ID);

        /* 5th: ordering as requested */
        if(options.order_by != ContactQueryOption.ORDER_BY.NO_ORDERING) {
            switch (options.order_by) {
                case LAST_TIME_MET:
                    query.append(" ORDER BY " + ContactDatabase.LAST_MET + " DESC ");
                    break;
            }
        }

        /* 6th: limiting the number of answer */
        if(options.answerLimit > 0) {
            query.append(" LIMIT ? ");
            argumentList.add(Integer.toString(options.answerLimit));
        }

        Log.d(TAG, "[Q] query: "+query.toString());
        for(String argument : argumentList) {
            Log.d(TAG, argument+" ");
        }

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery(query.toString(),argumentList.toArray(new String[argumentList.size()]));
        if(cursor == null)
            return null;

        try {
            ArrayList<Contact> ret = new ArrayList<Contact>();
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursorToContact(cursor));
            }
            return ret;
        } finally {
            cursor.close();
        }
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

    public long getContactDBID(String uid) {
        long ret = -1;
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, new String[] {ID}, UID+ " = ?", new String[] {uid}, null, null, null);
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
        contentValues.put(LAST_MET, contact.lastMet());
        contentValues.put(NB_STATUS_SENT, contact.nbStatusSent());
        contentValues.put(NB_STATUS_RCVD, contact.nbStatusReceived());

        long contactDBID = getContactDBID(contact.getUid());

        if(contactDBID < 0) {
            contactDBID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
            EventBus.getDefault().post(new ContactInsertedEvent(contact));
        } else {
            databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, UID + " = ?", new String[]{contact.getUid()});
            EventBus.getDefault().post(new ContactUpdatedEvent(contact));
        }

        // if we update the local contact, we delete the cache
        if(contact.isLocal())
            localContact = null;

        return contactDBID;
    }

    private Contact cursorToContact(final Cursor cursor) {
        if(cursor == null)
            return null;
        long contactDBID   = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        String author      = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
        String uid         = cursor.getString(cursor.getColumnIndexOrThrow(UID));
        boolean local      = (cursor.getInt(cursor.getColumnIndexOrThrow(LOCALUSER)) == 1);
        long date          = cursor.getLong(cursor.getColumnIndexOrThrow(LAST_MET));
        int nb_status_sent = cursor.getInt(cursor.getColumnIndexOrThrow(NB_STATUS_SENT));
        int nb_status_rcvd = cursor.getInt(cursor.getColumnIndexOrThrow(NB_STATUS_RCVD));

        Contact contact  = new Contact(author, uid, local);
        contact.lastMet(date);
        contact.setHashtagInterests(getHashtagsOfInterest(contactDBID));
        contact.setJoinedGroupIDs(getJoinedGroupIDs(contactDBID));
        contact.setInterfaces(getInterfaces(contactDBID));
        contact.setStatusSent(nb_status_sent);
        contact.setStatusReceived(nb_status_rcvd);
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
                    ret.put(cursor.getString(cursor.getColumnIndexOrThrow(HashtagDatabase.HASHTAG)),
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
                            " JOIN " + ContactGroupDatabase.TABLE_NAME + " c" +
                            " ON g." + GroupDatabase.ID + " = c." + ContactGroupDatabase.GDBID +
                            " WHERE c." + ContactGroupDatabase.UDBID + " = ?");
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

    public Set<Interface> getInterfaces(long contactDBID) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            StringBuilder query = new StringBuilder(
                    "SELECT i.* FROM " + InterfaceDatabase.TABLE_NAME + " i" +
                            " JOIN " + ContactInterfaceDatabase.TABLE_NAME + " ci" +
                            " ON i." + InterfaceDatabase.ID + " = ci." + ContactInterfaceDatabase.INTERFACE_DBID +
                            " JOIN " + ContactDatabase.TABLE_NAME + " c" +
                            " ON c." + ContactDatabase.ID + " = ci." + ContactInterfaceDatabase.CONTACT_DBID +
                            " WHERE c." + ContactDatabase.ID + " = ?");
            cursor = database.rawQuery(query.toString(), new String[]{Long.toString(contactDBID)});
            Set<Interface> ret = new HashSet<Interface>();
            if (cursor != null) {
                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    ret.add(InterfaceDatabase.cursorToInterface(cursor));
                }
            }
            return ret;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

}


