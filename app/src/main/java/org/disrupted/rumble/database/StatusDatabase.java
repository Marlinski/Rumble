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

import org.disrupted.rumble.message.StatusMessage;

import java.util.List;

/**
 * @author Marlinski
 */
public class StatusDatabase extends Database {

    private static final String TAG = "StatusDatabase";

    public static final String TABLE_NAME       = "status";
    public static final String ID               = "_id";
    public static final String AUTHOR           = "author";
    public static final String POST             = "post";
    public static final String FILE_ID          = "file";
    public static final String FILE_NAME        = "filename";
    public static final String TIME_OF_CREATION = "toc";
    public static final String TIME_OF_ARRIVAL  = "toa";
    public static final String HOP_COUNT        = "hopcount";
    public static final String FORWARDER_LIST   = "fwdlist";
    public static final String SCORE            = "score";
    public static final String TTL              = "ttl";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID        + " INTEGER PRIMARY KEY, "
                 + AUTHOR    + " TEXT, "
                 + POST      + " TEXT, "
                 + FILE_ID   + " INTEGER, "
                 + FILE_NAME + " TEXT, "
                 + TIME_OF_CREATION  + " INTEGER, "
                 + TIME_OF_ARRIVAL   + " INTEGER, "
                 + HOP_COUNT + " INTEGER, "
                 + FORWARDER_LIST  + " TEXT, "
                 + SCORE     + " INTEGER, "
                 + TTL       + " INTEGER"
          + " );";

    public StatusDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
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
                " WHERE h."+HashtagDatabase.HASHTAG+" = ?");

        String[] array = new String[filters.size()];
        for(int i = 1; i < filters.size(); i++) {
            query.append(" OR h."+HashtagDatabase.HASHTAG+" = ?");
        }
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
        return db.delete(TABLE_NAME, ID_WHERE, new String[]{statusID + ""});
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
        contentValues.put(AUTHOR, status.getAuthor());
        contentValues.put(POST, status.getPost());
        contentValues.put(FILE_ID, status.getFileID());
        contentValues.put(FILE_NAME, status.getFileName());
        /*
        //Attached file not yet supported
        if(message.getFilePath() != "") {
            DatabaseFactory.getDocumentDatabase().insertFile(message.getFilePath());
        }
        */
        contentValues.put(TIME_OF_ARRIVAL, status.getTimeOfArrival());
        contentValues.put(TIME_OF_CREATION, status.getTimeOfCreation());
        contentValues.put(HOP_COUNT, status.getHopCount());
        contentValues.put(FORWARDER_LIST, status.getForwarderList());
        contentValues.put(SCORE, status.getScore());
        contentValues.put(TTL, status.getTTL());

        long statusID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);

        if(statusID >= 0) {
            for (String hashtag : status.getHashtagSet()) {
                long tagID = DatabaseFactory.getHashtagDatabase(context).insertHashtag(hashtag);
                long statusTagID = DatabaseFactory.getStatusTagDatabase(context).insertStatusTag(tagID, statusID);
            }
        }

        this.notifyStatusListListener(status);

        return statusID;
    }


}
