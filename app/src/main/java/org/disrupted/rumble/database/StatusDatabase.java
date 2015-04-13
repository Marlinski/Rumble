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

import org.disrupted.rumble.database.events.StatusDatabaseEvent;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.message.StatusMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
    public static final String HOP_LIMIT        = "hoplimit";    // number of device it has traversed
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
                 + HOP_LIMIT   + " INTEGER, "
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

    public static class StatusQueryOption {
        public static final long FILTER_READ  = 0x0001;
        public static final long FILTER_GROUP = 0x0002;
        public static final long FILTER_HOPS  = 0x0004;
        public static final long FILTER_LIKE  = 0x0010;
        public static final long FILTER_TAG   = 0x0020;
        public static final long FILTER_USER  = 0x0040;
        public static final long FILTER_TOC_FROM  = 0x0080;
        public static final long FILTER_TOA_FROM  = 0x0100;

        public long         filterFlags;
        public List<String> hashtagFilters;
        public String       groupName;
        public String       userName;
        public int          hopLimit;
        public long         from_toc;
        public long         from_toa;
        public int          answerLimit;

        public StatusQueryOption() {
            filterFlags = 0x00;
            hashtagFilters = null;
            groupName = GroupDatabase.DEFAULT_GROUP;
            userName = null;
            from_toc = 0;
            from_toa = 0;
            answerLimit = 20;
        }
    }


    public StatusDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public boolean getStatusesId(StatusIdQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<Integer> read() {
                        return getStatusesId();
                    }
                }, callback);
    }
    private ArrayList<Integer> getStatusesId() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, new String[]{ID}, null, null, null, null, null);
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

    public boolean getStatuses(final StatusQueryOption options, StatusQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public ArrayList<StatusMessage> read() {
                        return getStatuses(options);
                    }
                }, callback);
    }
    private ArrayList<StatusMessage> getStatuses(StatusQueryOption options) {
        boolean groupby = false;
        StringBuilder query = new StringBuilder(
                "SELECT * FROM "+StatusDatabase.TABLE_NAME+" s");

        List<String> argumentList = new ArrayList<String>();
        if(options != null) {
            if (((options.filterFlags & options.FILTER_TAG) == options.FILTER_TAG) && (options.hashtagFilters != null) && (options.hashtagFilters.size() > 0)) {
                query.append(
                        " JOIN " + StatusTagDatabase.TABLE_NAME + " m" +
                        " ON s." + StatusDatabase.ID + " = m." + StatusTagDatabase.SID +
                        " JOIN " + HashtagDatabase.TABLE_NAME + " h" +
                        " ON h." + HashtagDatabase.ID + " = m." + StatusTagDatabase.HID +
                        " WHERE ( ( lower(h." + HashtagDatabase.HASHTAG + ") = lower(?)");
                Iterator<String> it = options.hashtagFilters.iterator();
                String hashtag = it.next();
                argumentList.add(hashtag);
                while (it.hasNext()) {
                    hashtag = it.next();
                    query.append(" OR lower(h." + HashtagDatabase.HASHTAG + ") = lower(?) ");
                    argumentList.add(hashtag);
                }
                query.append(" ) ");
                groupby = true;
            } else if (options.filterFlags > 0) {
                query.append(" WHERE ( ");
            }

            if (((options.filterFlags & StatusQueryOption.FILTER_GROUP) == StatusQueryOption.FILTER_GROUP)) {
                query.append(" AND s." + StatusDatabase.GROUP + " = lower(?) ");
                argumentList.add(options.groupName);
            }
            if (((options.filterFlags & StatusQueryOption.FILTER_USER) == StatusQueryOption.FILTER_USER) && (options.userName != null)) {
                query.append(" AND s." + StatusDatabase.AUTHOR + " = lower(?) ");
                argumentList.add(options.userName);
            }
            if ((options.filterFlags & StatusQueryOption.FILTER_TOC_FROM) == StatusQueryOption.FILTER_TOC_FROM) {
                query.append(" AND s." + StatusDatabase.TIME_OF_CREATION + " > ? ");
                argumentList.add(Long.toString(options.from_toc));
            }
            if ((options.filterFlags & StatusQueryOption.FILTER_TOA_FROM) == StatusQueryOption.FILTER_TOA_FROM) {
                query.append(" AND s." + StatusDatabase.TIME_OF_ARRIVAL + " > ? ");
                argumentList.add(Long.toString(options.from_toa));
            }
            if ((options.filterFlags & StatusQueryOption.FILTER_HOPS) == StatusQueryOption.FILTER_HOPS) {
                query.append(" AND s." + StatusDatabase.HOP_LIMIT + " = ? ");
                argumentList.add(Integer.toString(options.hopLimit));
            }
            if ((options.filterFlags & StatusQueryOption.FILTER_READ) == StatusQueryOption.FILTER_READ) {
                query.append(" AND s." + StatusDatabase.USERREAD + " = 1 ");
            }
            if ((options.filterFlags & StatusQueryOption.FILTER_LIKE) == StatusQueryOption.FILTER_LIKE) {
                query.append(" AND s." + StatusDatabase.USERLIKED + " = 1 ");
            }

            if (options.filterFlags > 0)
                query.append(" ) ");

            if (groupby)
                query.append(" GROUP BY " + StatusDatabase.ID);

            query.append(" ORDER BY "+StatusDatabase.TIME_OF_CREATION+" DESC LIMIT ?");
            argumentList.add(Integer.toString(options.answerLimit));
        }

        //Log.d(TAG, "[Q] query: "+query.toString());

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery(query.toString(),argumentList.toArray(new String[argumentList.size()]));
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


    public StatusMessage getStatus(String  uuid) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, UUID + " = ?", new String[]{ uuid }, null, null, null);
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
        Cursor cursor = database.query(TABLE_NAME, new String[]{ID}, UUID+ " = ?", new String[]{new String(uuid)}, null, null, null);
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
        if(!status.getFileName().equals(""))
            contentValues.put(FILE_NAME, status.getFileName());

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

    public void clearStatus(final DatabaseExecutor.WritableQueryCallback callback) {
        DatabaseFactory.getDatabaseExecutor(context).addQuery(
            new DatabaseExecutor.WritableQuery() {
                @Override
                public boolean write() {
                    clearStatus();
                    return true;
                }
            }, callback);
    }
    public void clearStatus() {
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.execSQL("DROP TABLE " + TABLE_NAME + ";");
        db.execSQL(CREATE_TABLE);
        db.execSQL("DROP TABLE " + ForwarderDatabase.TABLE_NAME + ";");
        db.execSQL(ForwarderDatabase.CREATE_TABLE);
        db.execSQL("DROP TABLE " + StatusTagDatabase.TABLE_NAME + ";");
        db.execSQL(StatusTagDatabase.CREATE_TABLE);
        EventBus.getDefault().post(new StatusDatabaseEvent());
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
            cursorForwarders = DatabaseFactory.getForwarderDatabase(context).getForwarderList(statusID);
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
