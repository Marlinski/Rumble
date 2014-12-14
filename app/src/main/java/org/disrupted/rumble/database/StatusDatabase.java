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
import org.disrupted.rumble.database.events.NewStatusEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.message.StatusMessage;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
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
    public static final String SCORE            = "score";       // reserved
    public static final String AUTHOR           = "author";      // original author of the post
    public static final String POST             = "post";        // the post
    public static final String FILE_NAME        = "filename";    // the name of the attached file
    public static final String TIME_OF_CREATION = "toc";         // time of creation of the post
    public static final String TIME_OF_ARRIVAL  = "toa";         // time of arrival at current node
    public static final String HOP_COUNT        = "hopcount";    // number of device it has traversed
    public static final String FORWARDER_LIST   = "fwdlist";     // obsolete (another table)
    public static final String TTL              = "ttl";         // time to live (in second since toc)
    public static final String LIKE             = "like";        // number of like (in the path)
    public static final String REPLICATION      = "replication"; // number of replications
    public static final String READ             = "read";        // has the user read it already ?

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
                 + UUID        + " TEXT, "
                 + SCORE       + " INTEGER, "
                 + AUTHOR      + " TEXT, "
                 + POST        + " TEXT, "
                 + FILE_NAME   + " TEXT, "
                 + TIME_OF_CREATION  + " INTEGER, "
                 + TIME_OF_ARRIVAL   + " INTEGER, "
                 + HOP_COUNT + " INTEGER, "
                 + FORWARDER_LIST  + " TEXT, "
                 + TTL       + " INTEGER, "
                 + LIKE      + " INTEGER, "
                 + REPLICATION + " INTEGER, "
                 + READ        + " INTEGER, "
                 + "UNIQUE ( " + UUID + " )"
          + " );";

    public StatusDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public boolean getStatusesForScoring(DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Cursor read() {
                        return getStatusesForScoring();
                    }
                }, callback);
    }
    private Cursor getStatusesForScoring() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, new String[] {ID, HOP_COUNT, TTL, LIKE, REPLICATION, TIME_OF_CREATION}, null, null, null, null, null);
        return cursor;
    }

    public static StatusMessage cursorToStatus(Cursor cursor) {
        if(cursor == null)
            return null;
        if(cursor.isAfterLast())
            return null;

        long statusID          = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        String uuid            = cursor.getString(cursor.getColumnIndexOrThrow(UUID));
        String author          = cursor.getString(cursor.getColumnIndexOrThrow(AUTHOR));
        String post            = cursor.getString(cursor.getColumnIndexOrThrow(POST));
        long toc               = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_CREATION));
        String filename        = cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME));
        long     toa           = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_ARRIVAL));;
        Integer hopCount       = cursor.getInt(cursor.getColumnIndexOrThrow(HOP_COUNT));
        long ttl               = cursor.getLong(cursor.getColumnIndexOrThrow(TTL));
        Integer like           = cursor.getInt(cursor.getColumnIndexOrThrow(LIKE));
        Integer replication    = cursor.getInt(cursor.getColumnIndexOrThrow(REPLICATION));
        boolean read           = (cursor.getInt(cursor.getColumnIndexOrThrow(READ)) == 1);

        Cursor hashsetCursor   = DatabaseFactory.getHashtagDatabase(RumbleApplication.getContext()).getHashtag(statusID);
        Set<String> hashtagSet = new HashSet<String>();
        for (hashsetCursor.moveToFirst(); !hashsetCursor.isAfterLast(); hashsetCursor.moveToNext()) {
            hashtagSet.add(hashsetCursor.getString(hashsetCursor.getColumnIndexOrThrow(HashtagDatabase.HASHTAG)));
        }
        hashsetCursor.close();

        Cursor cursorForwarders = DatabaseFactory.getForwarderDatabase(RumbleApplication.getContext()).getForwarderList(statusID);
        Set<String> forwarders = new HashSet<String>();
        if(cursorForwarders != null) {
            for (cursorForwarders.moveToFirst(); !cursorForwarders.isAfterLast(); cursorForwarders.moveToNext()) {
                forwarders.add(cursorForwarders.getString(cursorForwarders.getColumnIndexOrThrow(ForwarderDatabase.RECEIVEDBY)));
            }
            cursorForwarders.close();
        }

        StatusMessage message = new StatusMessage(post, author, toc);
        message.setdbId(statusID);
        message.setUuid(uuid);
        message.setHashtagSet(hashtagSet);
        message.setTimeOfArrival(toa);
        message.setFileName(filename);
        message.setHopCount(hopCount);
        message.setTTL(ttl);
        message.setLike(like);
        message.addReplication(replication);
        message.setRead(read);
        message.setForwarderList(forwarders);

        return message;
    }


    public Cursor getBatchStatus(List<Integer> idList) {
        if(idList.size() == 0)
            return null;

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Iterator<Integer> it = idList.iterator();
        StringBuilder whereClause = new StringBuilder(ID+" IN ( " + it.next());
        while(it.hasNext()) {
            whereClause.append(", "+it.next());
        }
        whereClause.append(" ) ");
        Cursor cursor = database.query(TABLE_NAME, null, whereClause.toString(), null, null, null, null);
        return cursor;
    }


    public boolean getStatuses(DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Cursor read() {
                        return getStatuses();
                    }
                }, callback);
    }
    private Cursor getStatuses() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        return cursor;
    }


    public boolean getFilteredStatuses(final List<String> filters, DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Cursor read() {
                        return getFilteredStatuses(filters);
                    }
                }, callback);
    }
    private Cursor getFilteredStatuses(List<String> filters) {
        if(filters.size() == 0)
            return getStatuses();

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        StringBuilder query = new StringBuilder(
                "SELECT * FROM "+StatusDatabase.TABLE_NAME+" s"+
                " JOIN " + StatusTagDatabase.TABLE_NAME+" m"+
                " ON s."+StatusDatabase.ID+" = m."+StatusTagDatabase.SID  +
                " JOIN "+HashtagDatabase.TABLE_NAME+" h"                     +
                " ON h."+HashtagDatabase.ID+" = m."+StatusTagDatabase.HID +
                " WHERE lower(h."+HashtagDatabase.HASHTAG+") = lower(?)");


        String[] array = new String[filters.size()];
        for(int i = 1; i < filters.size(); i++) {
            query.append(" OR lower(h."+HashtagDatabase.HASHTAG+") = lower(?)");
        }
        query.append(" GROUP BY "+StatusDatabase.ID);
        Cursor cursor = database.rawQuery(query.toString(),filters.toArray(array));
        return cursor;
    }


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
        DatabaseFactory.getStatusTagDatabase(context).deleteEntriesMatchingStatusID(statusID);
        int count = db.delete(TABLE_NAME, ID_WHERE, new String[]{statusID + ""});
        if(count > 0)
            EventBus.getDefault().post(new StatusDeletedEvent(statusID));
        return count;
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
        contentValues.put(UUID, status.getUuid());
        contentValues.put(SCORE, status.getScore());
        contentValues.put(AUTHOR, status.getAuthor());
        contentValues.put(POST, status.getPost());
        contentValues.put(FILE_NAME, status.getFileName());
        contentValues.put(TIME_OF_ARRIVAL, status.getTimeOfArrival());
        contentValues.put(TIME_OF_CREATION, status.getTimeOfCreation());
        contentValues.put(HOP_COUNT, status.getHopCount());
        contentValues.put(LIKE, status.getLike());
        contentValues.put(TTL, status.getTTL());
        contentValues.put(REPLICATION, status.getReplication());
        contentValues.put(READ, status.hasBeenReadAlready() ? 1 : 0);

        int count =  databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, ID+" = "+status.getdbId(), null);
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
        contentValues.put(SCORE, status.getScore());
        contentValues.put(AUTHOR, status.getAuthor());
        contentValues.put(POST, status.getPost());
        contentValues.put(FILE_NAME, status.getFileName());
        contentValues.put(TIME_OF_ARRIVAL, status.getTimeOfArrival());
        contentValues.put(TIME_OF_CREATION, status.getTimeOfCreation());
        contentValues.put(HOP_COUNT, status.getHopCount());
        contentValues.put(LIKE, status.getLike());
        contentValues.put(TTL, status.getTTL());
        contentValues.put(REPLICATION, status.getReplication());
        contentValues.put(READ, status.hasBeenReadAlready() ? 1 : 0);

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
            EventBus.getDefault().post(new NewStatusEvent(status));
        }

        return statusID;
    }


}
