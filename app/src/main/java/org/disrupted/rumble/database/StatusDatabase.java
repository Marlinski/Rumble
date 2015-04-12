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

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.message.StatusMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatusDatabase extends Database {

    private static final String TAG = "StatusDatabase";

    public static final String TABLE_NAME       = "status";
    public static final String ID               = "_id";
    public static final String UUID             = "uuid";        // unique ID 128 bits
    public static final String AUTHOR           = "author";      // original author of the post
    public static final String GROUP            = "group_name";  // the name of the group it belongs to
    public static final String POST             = "post";        // the post itself
    public static final String FILE_NAME        = "filename";    // the name of the attached file
    public static final String TIME_OF_CREATION = "toc";         // time of creation of the post
    public static final String TIME_OF_ARRIVAL  = "toa";         // time of arrival at current node
    public static final String TTL              = "ttl";         // time to live (in second since toc)
    public static final String HOP_COUNT        = "hopcount";    // number of device it has traversed
    public static final String LIKE             = "like";        // number of like (in the path)
    public static final String REPLICATION      = "replication"; // number of replications
    public static final String DUPLICATE        = "duplicate";   // number of copies received
    public static final String USERREAD         = "read";        // has the user read it already ?
    public static final String USERLIKED         = "liked";        // has the user liked it ?
    public static final String USERSAVED         = "saved";        // has the user liked it ?

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
                 + UUID        + " TEXT, "
                 + AUTHOR      + " TEXT, "
                 + GROUP       + " TEXT, "
                 + POST        + " TEXT, "
                 + FILE_NAME   + " TEXT, "
                 + TIME_OF_CREATION  + " INTEGER, "
                 + TIME_OF_ARRIVAL   + " INTEGER, "
                 + TTL         + " INTEGER, "
                 + LIKE        + " INTEGER, "
                 + HOP_COUNT   + " INTEGER, "
                 + REPLICATION + " INTEGER, "
                 + DUPLICATE   + " INTEGER, "
                 + USERREAD    + " INTEGER, "
                 + USERLIKED   + " INTEGER, "
                 + USERSAVED   + " INTEGER, "
                 + "UNIQUE ( " + UUID + " ), "
                 + "FOREIGN KEY ( "+ GROUP + " ) REFERENCES " + GroupDatabase.TABLE_NAME + " ( " + GroupDatabase.NAME   + " ) "
          + " );";

    public static abstract class StatusQueryCallback implements DatabaseExecutor.ReadableQueryCallback {
        public final void onReadableQueryFinished(Object object) {
            if(object instanceof ArrayList)
                onStatusQueryFinished((ArrayList<StatusMessage>)(object));
        }
        public abstract void onStatusQueryFinished(ArrayList<StatusMessage> statuses);
    }
    public static abstract class StatusIdQueryCallback implements DatabaseExecutor.ReadableQueryCallback {
        public final void onReadableQueryFinished(Object object) {
            if(object instanceof ArrayList)
                onStatusIdQueryFinished((ArrayList<Integer>)(object));
        }
        public abstract void onStatusIdQueryFinished(ArrayList<Integer> statusIds);
    }

    public StatusDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public boolean getStatusesIdForUser(final String hash, StatusIdQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<Integer> read() {
                        return getStatusesIdForUser(hash);
                    }
                }, callback);
    }
    private ArrayList<Integer> getStatusesIdForUser(final String hash) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        StringBuilder query = new StringBuilder(
                "SELECT s."+ID+" FROM "+StatusDatabase.TABLE_NAME+" s"+
                        " JOIN " + ForwarderDatabase.TABLE_NAME+" f"+
                        " ON s."+StatusDatabase.ID+" = f."+ForwarderDatabase.ID  +
                        " WHERE f."+ForwarderDatabase.RECEIVEDBY+" != ?" +
                        " GROUP BY s."+StatusDatabase.ID);
        Cursor cursor = database.rawQuery(query.toString(), new String[]{ hash });
        if(cursor == null)
            return null;
        ArrayList<Integer> ret = new ArrayList<Integer>();
        try {
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursor.getInt(cursor.getColumnIndexOrThrow(ID)));
            }
        }finally {
            cursor.close();
        }
        return ret;
    }

    public boolean getStatuses(StatusQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<StatusMessage> read() {
                        return getStatuses();
                    }
                }, callback);
    }
    private ArrayList<StatusMessage> getStatuses() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        if(cursor == null)
            return null;
        ArrayList<StatusMessage> ret = new ArrayList<StatusMessage>();
        try {
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursorToStatus(cursor));
            }
        }finally {
            cursor.close();
        }
        return ret;
    }

    public boolean getFilteredStatuses(final List<String> filters, StatusQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<StatusMessage> read() {
                        return getFilteredStatuses(filters);
                    }
                }, callback);
    }
    private ArrayList<StatusMessage> getFilteredStatuses(List<String> hashtagFilters) {
        if(hashtagFilters.size() == 0)
            return getStatuses();

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        StringBuilder query = new StringBuilder(
                "SELECT * FROM "+StatusDatabase.TABLE_NAME+" s"+
                " JOIN " + StatusTagDatabase.TABLE_NAME+" m"+
                " ON s."+StatusDatabase.ID+" = m."+StatusTagDatabase.SID  +
                " JOIN "+HashtagDatabase.TABLE_NAME+" h"                     +
                " ON h."+HashtagDatabase.ID+" = m."+StatusTagDatabase.HID +
                " WHERE lower(h."+HashtagDatabase.HASHTAG+") = lower(?)");


        String[] array = new String[hashtagFilters.size()];
        for(int i = 1; i < hashtagFilters.size(); i++) {
            query.append(" OR lower(h."+HashtagDatabase.HASHTAG+") = lower(?)");
        }
        query.append(" GROUP BY "+StatusDatabase.ID);
        Cursor cursor = database.rawQuery(query.toString(),hashtagFilters.toArray(array));
        if(cursor == null)
            return null;

        ArrayList<StatusMessage> ret = new ArrayList<StatusMessage>();
        try {
            for(cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursorToStatus(cursor));
            }
        }finally {
            cursor.close();
        }
        return ret;
    }

    public StatusMessage getStatus(String uuid) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, UUID + " = ?", new String[]{uuid}, null, null, null);
        if(cursor == null)
            return null;
        try {
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToStatus(cursor);
            else
                return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }
    public StatusMessage getStatus(long id) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, null, ID + " = ?", new String[]{Long.toString(id)}, null, null, null);
            if(cursor == null)
                return null;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToStatus(cursor);
            else
                return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    // todo delete attached file too
    public boolean deleteStatus(final long statusID, DatabaseExecutor.WritableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (deleteStatus(statusID) > 0);
                    }
                }, callback);
    }
    private long deleteStatus(long statusID) {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        int count = db.delete(TABLE_NAME, ID_WHERE, new String[]{statusID + ""});
        if(count > 0) {
            DatabaseFactory.getStatusTagDatabase(context).deleteEntriesMatchingStatusID(statusID);
            DatabaseFactory.getForwarderDatabase(context).deleteEntriesMatchingStatusID(statusID);
            EventBus.getDefault().post(new StatusDeletedEvent(statusID));
        }
        return count;
    }

    public boolean deleteStatus(final String uuid, DatabaseExecutor.WritableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (deleteStatus(uuid) > 0);
                    }
                }, callback);
    }
    private long deleteStatus(String uuid) {
        long total = 0;
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, new String[]{ID}, UUID+ " = ?", new String[]{uuid}, null, null, null);
        if(cursor == null) {
            Log.d(TAG, "status not found" );
            return total;
        }
        try {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                Log.d(TAG, "status found with ID "+id );
                total += deleteStatus(id);
            }
        } finally {
            cursor.close();
        }
        Log.d(TAG, total+" status deleted" );
        return total;
    }

    public boolean updateStatus(final StatusMessage status, final DatabaseExecutor.WritableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (updateStatus(status) >= 0);
                    }
                }, callback);
    }
    private int updateStatus(StatusMessage status){
        ContentValues contentValues = new ContentValues();
        contentValues.put(HOP_COUNT, status.getHopCount());
        contentValues.put(LIKE, status.getLike());
        contentValues.put(REPLICATION, status.getReplication());
        contentValues.put(DUPLICATE, status.getDuplicate());
        contentValues.put(USERREAD, status.hasUserReadAlready() ? 1 : 0);
        contentValues.put(USERLIKED, status.hasUserLiked() ? 1 : 0);
        contentValues.put(USERSAVED, status.hasUserSaved() ? 1 : 0);

        int count = databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID + " = " + status.getdbId(), null);

        if(count > 0) {
            for (String forwarder : status.getForwarderList()) {
                DatabaseFactory.getForwarderDatabase(context).insertForwarder(status.getdbId(), forwarder);
            }
            EventBus.getDefault().post(new StatusUpdatedEvent(status));
        }
        return count;
    }

    public boolean insertStatus(final StatusMessage status, final DatabaseExecutor.WritableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.WritableQuery() {
                    @Override
                    public boolean write() {
                        return (insertStatus(status) >= 0);
                    }
                }, callback);
    }
    private long insertStatus(StatusMessage status){
        ContentValues contentValues = new ContentValues();
        contentValues.put(UUID, status.getUuid());
        contentValues.put(AUTHOR, status.getAuthor());
        contentValues.put(POST, status.getPost());
        contentValues.put(GROUP, status.getGroup());
        contentValues.put(FILE_NAME, status.getFileName());
        contentValues.put(TIME_OF_ARRIVAL, status.getTimeOfArrival());
        contentValues.put(TIME_OF_CREATION, status.getTimeOfCreation());
        contentValues.put(HOP_COUNT, status.getHopCount());
        contentValues.put(LIKE, status.getLike());
        contentValues.put(TTL, status.getTTL());
        contentValues.put(REPLICATION, status.getReplication());
        contentValues.put(DUPLICATE, status.getDuplicate());
        contentValues.put(USERREAD, status.hasUserReadAlready() ? 1 : 0);
        contentValues.put(USERLIKED, status.hasUserLiked() ? 1 : 0);
        contentValues.put(USERSAVED, status.hasUserLiked() ? 1 : 0);

        long statusID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        status.setdbId(statusID);

        if(statusID >= 0) {
            for (String hashtag : status.getHashtagSet()) {
                long tagID = DatabaseFactory.getHashtagDatabase(context).insertHashtag(hashtag.toLowerCase());
                if(tagID >=0 )
                    DatabaseFactory.getStatusTagDatabase(context).insertStatusTag(tagID, statusID);
            }

            for (String forwarder : status.getForwarderList()) {
                DatabaseFactory.getForwarderDatabase(context).insertForwarder(statusID, forwarder);
            }
            EventBus.getDefault().post(new StatusInsertedEvent(status));
        }

        return statusID;
    }

    public boolean exists(String uuid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, new String[]{ID}, UUID + " = ?", new String[]{uuid}, null, null, null);
            if(cursor == null)
                return false;
            else
                return (cursor.getCount() > 0);
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    /*
     * utility function to transform a row into a StatusMessage
     * ! this method does not close the cursor
     */
    private StatusMessage cursorToStatus(final Cursor cursor) {
        if(cursor == null)
            return null;
        if(cursor.isAfterLast())
            return null;

        long statusID = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        String author = cursor.getString(cursor.getColumnIndexOrThrow(AUTHOR));
        long toc      = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_CREATION));
        String post   = cursor.getString(cursor.getColumnIndexOrThrow(POST));

        StatusMessage message = new StatusMessage(post, author, toc);
        message.setdbId(statusID);
        message.setGroup(cursor.getString(cursor.getColumnIndexOrThrow(GROUP)));
        message.setUuid(cursor.getString(cursor.getColumnIndexOrThrow(UUID)));
        message.setTimeOfArrival(cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_ARRIVAL)));
        message.setFileName(cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME)));
        message.setHopCount(cursor.getInt(cursor.getColumnIndexOrThrow(HOP_COUNT)));
        message.setTTL(cursor.getLong(cursor.getColumnIndexOrThrow(TTL)));
        message.setLike(cursor.getInt(cursor.getColumnIndexOrThrow(LIKE)));
        message.addReplication(cursor.getInt(cursor.getColumnIndexOrThrow(REPLICATION)));
        message.addDuplicate(cursor.getInt(cursor.getColumnIndexOrThrow(DUPLICATE)));
        message.setUserRead((cursor.getInt(cursor.getColumnIndexOrThrow(USERREAD)) == 1));
        message.setUserLike((cursor.getInt(cursor.getColumnIndexOrThrow(USERLIKED)) == 1));
        message.setUserSaved((cursor.getInt(cursor.getColumnIndexOrThrow(USERSAVED)) == 1));
        message.setHashtagSet(getHashTagList(statusID));
        message.setForwarderList(getForwarderList(statusID));
        return message;
    }

    private Set<String> getHashTagList(long statusID) {
        Cursor hashsetCursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            StringBuilder query = new StringBuilder(
                    "SELECT h." + HashtagDatabase.ID + " FROM " + StatusDatabase.TABLE_NAME + " s" +
                            " JOIN " + StatusTagDatabase.TABLE_NAME + " m" +
                            " ON s." + StatusDatabase.ID + " = m." + StatusTagDatabase.SID +
                            " JOIN " + HashtagDatabase.TABLE_NAME + " h" +
                            " ON h." + HashtagDatabase.ID + " = m." + StatusTagDatabase.HID +
                            " WHERE s." + StatusDatabase.ID + " = ?");
            hashsetCursor = database.rawQuery(query.toString(), new String[]{Long.toString(statusID)});
            Set<String> hashtagSet = new HashSet<String>();
            if (hashsetCursor != null) {
                for (hashsetCursor.moveToFirst(); !hashsetCursor.isAfterLast(); hashsetCursor.moveToNext()) {
                    hashtagSet.add(hashsetCursor.getString(hashsetCursor.getColumnIndexOrThrow(HashtagDatabase.ID)));
                }
            }
            return hashtagSet;
        } finally {
            if(hashsetCursor != null)
                hashsetCursor.close();
        }
    }

    private Set<String> getForwarderList(long statusID) {
        Cursor cursorForwarders = null;
        try {
            cursorForwarders = DatabaseFactory.getForwarderDatabase(RumbleApplication.getContext()).getForwarderList(statusID);
            Set<String> forwarders = new HashSet<String>();
            if (cursorForwarders != null) {
                for (cursorForwarders.moveToFirst(); !cursorForwarders.isAfterLast(); cursorForwarders.moveToNext()) {
                    forwarders.add(cursorForwarders.getString(cursorForwarders.getColumnIndexOrThrow(ForwarderDatabase.RECEIVEDBY)));
                }
            }
            return forwarders;
        } finally {
            if(cursorForwarders != null)
                cursorForwarders.close();
        }
    }
}
