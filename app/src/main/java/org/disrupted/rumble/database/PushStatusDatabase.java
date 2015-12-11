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

import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.database.events.StatusWipedEvent;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class PushStatusDatabase extends Database {

    private static final String TAG = "PushStatusDatabase";

    public static final String TABLE_NAME         = "push_status";
    public static final String ID                 = "_id";
    public static final String UUID               = "uuid";         // unique ID 128 bits
    public static final String AUTHOR_DBID        = "author_db_id"; // unique ID 64  bits stored in Base64
    public static final String GROUP_DBID         = "group_db_id";  // the name of the group it belongs to
    public static final String POST               = "post";         // the post itself
    public static final String FILE_NAME          = "filename";     // the name of the attached file
    public static final String TIME_OF_CREATION   = "toc";          // time of creation of the post
    public static final String TIME_OF_ARRIVAL    = "toa";          // time of arrival at current node
    public static final String TIME_TO_LIVE       = "ttl";          // time to live (in second since toc)
    public static final String SENDER_DBID        = "sender_db_id"; // unique ID 64  bits stored in Base64
    public static final String HOP_COUNT          = "hopcount";     // number of device it has traversed
    public static final String HOP_LIMIT          = "hoplimit";     // number of device it has traversed
    public static final String LIKE               = "like";         // number of like (in the path)
    public static final String REPLICATION        = "replication";  // number of replications
    public static final String DUPLICATE          = "duplicate";    // number of copies received
    public static final String USERREAD           = "read";         // has the user read it already ?
    public static final String USERLIKED          = "liked";       // has the user liked it ?
    public static final String USERSAVED          = "saved";       // has the user liked it ?

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
                 + UUID        + " TEXT, "
                 + AUTHOR_DBID + " INTEGER, "
                 + GROUP_DBID  + " INTEGER, "
                 + POST        + " TEXT, "
                 + FILE_NAME   + " TEXT, "
                 + TIME_OF_CREATION   + " INTEGER, "
                 + TIME_OF_ARRIVAL    + " INTEGER, "
                 + SENDER_DBID + " INTEGER, "
                 + TIME_TO_LIVE + " INTEGER, "
                 + HOP_LIMIT   + " INTEGER, "
                 + LIKE        + " INTEGER, "
                 + HOP_COUNT   + " INTEGER, "
                 + REPLICATION + " INTEGER, "
                 + DUPLICATE   + " INTEGER, "
                 + USERREAD    + " INTEGER, "
                 + USERLIKED   + " INTEGER, "
                 + USERSAVED   + " INTEGER, "
                 + "UNIQUE ( " + UUID + " ), "
                 + "FOREIGN KEY ( "+ AUTHOR_DBID + " ) REFERENCES " + ContactDatabase.TABLE_NAME + " ( " + ContactDatabase.ID   + " ), "
                 + "FOREIGN KEY ( "+ SENDER_DBID + " ) REFERENCES " + ContactDatabase.TABLE_NAME + " ( " + ContactDatabase.ID   + " ), "
                 + "FOREIGN KEY ( "+ GROUP_DBID  + " ) REFERENCES " + GroupDatabase.TABLE_NAME   + " ( " + GroupDatabase.ID   + " ) "
          + " );";


    public static class StatusQueryOption {
        public static final long FILTER_READ               = 0x0001;
        public static final long FILTER_GROUP              = 0x0002;
        public static final long FILTER_HOPS               = 0x0004;
        public static final long FILTER_LIKE               = 0x0008;
        public static final long FILTER_TAG                = 0x0010;
        public static final long FILTER_AUTHOR             = 0x0020;
        public static final long FILTER_AFTER_TOC          = 0x0040;
        public static final long FILTER_AFTER_TOA          = 0x0080;
        public static final long FILTER_BEFORE_TOC         = 0x0100;
        public static final long FILTER_BEFORE_TOA         = 0x0200;
        public static final long FILTER_NEVER_SEND_TO_USER = 0x0400;
        public static final long FILTER_NOT_EXPIRED        = 0x0800;

        public enum QUERY_RESULT {
            COUNT,
            LIST_OF_MESSAGE,
            LIST_OF_DBIDS,
            LIST_OF_UUIDS
        }

        public enum ORDER_BY {
            NO_ORDERING,
            TIME_OF_CREATION,
            TIME_OF_ARRIVAL
        }

        public long         filterFlags;

        public boolean      read;
        public boolean      like;
        public Set<String>  hashtagFilters;
        public Set<String>  groupIDFilters;
        public int          hopLimit;
        public long         after_toc;
        public long         after_toa;
        public long         before_toc;
        public long         before_toa;
        public String       uid;
        public int          answerLimit;
        public ORDER_BY     order_by;
        public QUERY_RESULT query_result;

        public StatusQueryOption() {
            filterFlags = 0x00;
            read = false;
            like = false;
            hashtagFilters = null;
            groupIDFilters = null;
            uid      = null;
            after_toc = 0;
            before_toc = 0;
            after_toa = 0;
            before_toa = 0;
            answerLimit = 0;
            order_by = ORDER_BY.NO_ORDERING;
            query_result = QUERY_RESULT.LIST_OF_MESSAGE;
        }
    }

    public PushStatusDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    /*
         * General querying with options
         */
    public boolean getStatuses(final StatusQueryOption options, DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Object read() {
                        return getStatuses(options);
                    }
                }, callback);
    }
    private Object getStatuses(StatusQueryOption options) {
        if(options == null)
            options = new StatusQueryOption();

        /* 1st:  configure what the query will return */
        String select = " * ";
        switch (options.query_result) {
            case COUNT:
                select = " COUNT(*) ";
                break;
            case LIST_OF_DBIDS:
                select = " ps."+ID+" ";
                break;
            case LIST_OF_UUIDS:
                select = " ps."+UUID+" ";
                break;
            case LIST_OF_MESSAGE:
                select = " ps.* ";
                break;
        }

        StringBuilder query = new StringBuilder(
                "SELECT "+select+" FROM "+ PushStatusDatabase.TABLE_NAME+" ps"
        );

        boolean groupby = false;
        boolean firstwhere = true;
        List<String> argumentList = new ArrayList<String>();

        /* 2nd:  Join The tables as needed */
        boolean hashtagJoined = false;
        if (((options.filterFlags & StatusQueryOption.FILTER_TAG) == StatusQueryOption.FILTER_TAG) && (options.hashtagFilters != null) && (options.hashtagFilters.size() > 0)) {
            query.append(
                    " JOIN " + StatusTagDatabase.TABLE_NAME + " st" +
                    " ON ps." + PushStatusDatabase.ID + " = st." + StatusTagDatabase.SDBID +
                    " JOIN " + HashtagDatabase.TABLE_NAME + " h" +
                    " ON h." + HashtagDatabase.ID + " = st." + StatusTagDatabase.HDBID);
            hashtagJoined = true;
        }
        boolean contactJoined = false;
        if (((options.filterFlags & StatusQueryOption.FILTER_AUTHOR) == StatusQueryOption.FILTER_AUTHOR) && (options.uid != null)) {
            query.append(
                    " JOIN " + ContactDatabase.TABLE_NAME + " c" +
                    " ON ps." + PushStatusDatabase.AUTHOR_DBID + " = c." + ContactDatabase.ID);
            contactJoined = true;
        }
        boolean groupJoined = false;
        if (((options.filterFlags & StatusQueryOption.FILTER_GROUP) == StatusQueryOption.FILTER_GROUP) && (options.groupIDFilters != null) && (options.groupIDFilters.size() > 0)) {
            query.append(
                    " JOIN " + GroupDatabase.TABLE_NAME + " g" +
                    " ON ps." + PushStatusDatabase.GROUP_DBID + " = g." + GroupDatabase.ID);
            groupJoined = true;
        }

        /* 3rd:  Add the constraints */
        if (options.filterFlags > 0)
            query.append(" WHERE ( ");

        if(hashtagJoined) {
            firstwhere = false;
            query.append(" h."+HashtagDatabase.HASHTAG +" IN ( ? ");
            Iterator<String> it = options.hashtagFilters.iterator();
            argumentList.add(it.next().toLowerCase());
            while (it.hasNext()) {
                argumentList.add(it.next().toLowerCase());
                query.append(" , ? ");
            }
            query.append(" ) ");
            groupby = true;
        }
        if (contactJoined && (((options.filterFlags & StatusQueryOption.FILTER_AUTHOR) == StatusQueryOption.FILTER_AUTHOR)
                && (options.uid != null)) ) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" c." + ContactDatabase.UID + " = ? ");
            argumentList.add(options.uid);
        }
        if(groupJoined && ((options.filterFlags & StatusQueryOption.FILTER_GROUP) == StatusQueryOption.FILTER_GROUP)
                && (options.groupIDFilters != null)
                && (options.groupIDFilters.size() > 0) ) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" g."+GroupDatabase.GID +" IN ( ? ");
            Iterator<String> it = options.groupIDFilters.iterator();
            argumentList.add(it.next());
            while (it.hasNext()) {
                argumentList.add(it.next());
                query.append(" , ? ");
            }
            query.append(" ) ");
            groupby = true;
        }
        if(((options.filterFlags & StatusQueryOption.FILTER_NEVER_SEND_TO_USER) == StatusQueryOption.FILTER_NEVER_SEND_TO_USER)
                && (options.uid != null) ) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(
                    " ps." + PushStatusDatabase.ID + "  NOT IN ( "
                            + " SELECT sc." + StatusContactDatabase.STATUS_DBID
                            + " FROM " + StatusContactDatabase.TABLE_NAME + " sc "
                            + " JOIN " + ContactDatabase.TABLE_NAME + " c "
                            + " ON sc." + StatusContactDatabase.CONTACT_DBID + " = c." + ContactDatabase.ID
                            + " WHERE c." + ContactDatabase.UID + " = ? )");
            argumentList.add(options.uid);
            groupby = true;
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_AFTER_TOC) == StatusQueryOption.FILTER_AFTER_TOC) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" ps." + PushStatusDatabase.TIME_OF_CREATION + " >= ? ");
            argumentList.add(Long.toString(options.after_toc));
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_AFTER_TOA) == StatusQueryOption.FILTER_AFTER_TOA) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" ps." + PushStatusDatabase.TIME_OF_ARRIVAL + " >= ? ");
            argumentList.add(Long.toString(options.after_toa));
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_BEFORE_TOC) == StatusQueryOption.FILTER_BEFORE_TOC) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" ps." + PushStatusDatabase.TIME_OF_CREATION + " <= ? ");
            argumentList.add(Long.toString(options.before_toc));
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_BEFORE_TOA) == StatusQueryOption.FILTER_BEFORE_TOA) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" ps." + PushStatusDatabase.TIME_OF_ARRIVAL + " <= ? ");
            argumentList.add(Long.toString(options.before_toa));
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_HOPS) == StatusQueryOption.FILTER_HOPS) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" ps." + PushStatusDatabase.HOP_LIMIT + " = ? ");
            argumentList.add(Integer.toString(options.hopLimit));
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_READ) == StatusQueryOption.FILTER_READ) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            if(options.read)
                query.append(" ps." + PushStatusDatabase.USERREAD + " = 1 ");
            else
                query.append(" ps." + PushStatusDatabase.USERREAD + " = 0 ");
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_LIKE) == StatusQueryOption.FILTER_LIKE) {
            if(!firstwhere)
                query.append(" AND ");
            if(options.like)
                query.append(" ps." + PushStatusDatabase.USERLIKED + " = 1 ");
            else
                query.append(" ps." + PushStatusDatabase.USERLIKED + " = 0 ");
        }
        if ((options.filterFlags & StatusQueryOption.FILTER_NOT_EXPIRED) == StatusQueryOption.FILTER_NOT_EXPIRED) {
            if(!firstwhere)
                query.append(" AND ");
            long now = System.currentTimeMillis();
            query.append("( " +
                    " ps." + PushStatusDatabase.TIME_TO_LIVE + " < 0 " +
                    " OR  ? - ps." + PushStatusDatabase.TIME_OF_CREATION +
                    " < ps." + PushStatusDatabase.TIME_TO_LIVE +
                         " ) ");
            argumentList.add(Long.toString(now));
        }
        if (options.filterFlags > 0)
            query.append(" ) ");

        /* 4th: group by if necessary */
        if (groupby && (options.query_result != StatusQueryOption.QUERY_RESULT.COUNT))
            query.append(" GROUP BY ps." + PushStatusDatabase.ID);

        /* 5th: ordering as requested */
        if(options.order_by != StatusQueryOption.ORDER_BY.NO_ORDERING) {
            switch (options.order_by) {
                case TIME_OF_CREATION:
                    query.append(" ORDER BY " + PushStatusDatabase.TIME_OF_CREATION + " DESC ");
                    break;
                case TIME_OF_ARRIVAL:
                    query.append(" ORDER BY " + PushStatusDatabase.TIME_OF_ARRIVAL + " DESC ");
                    break;
            }
        }

        /* 6th: limiting the number of answer */
        if(options.answerLimit > 0) {
            query.append(" LIMIT ? ");
            argumentList.add(Integer.toString(options.answerLimit));
        }

        /* perform the query
        Log.d(TAG, "[Q] query: "+query.toString());
        for(String argument : argumentList) {
            Log.d(TAG, argument+" ");
        }*/

        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery(query.toString(),argumentList.toArray(new String[argumentList.size()]));
        if(cursor == null)
            return null;

        try {
            switch (options.query_result) {
                case COUNT:
                    cursor.moveToFirst();
                    return cursor.getInt(0);
                case LIST_OF_DBIDS:
                    ArrayList<Integer> listMessagesID = new ArrayList<Integer>();
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        listMessagesID.add(cursor.getInt(cursor.getColumnIndexOrThrow(ID)));
                    }
                    return listMessagesID;
                case LIST_OF_UUIDS:
                    ArrayList<String> listMessagesUUID = new ArrayList<String>();
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        listMessagesUUID.add(cursor.getString(cursor.getColumnIndexOrThrow(UUID)));
                    }
                    return listMessagesUUID;
                case LIST_OF_MESSAGE:
                    ArrayList<PushStatus> listIds = new ArrayList<PushStatus>();
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        listIds.add(cursorToStatus(cursor));
                    }
                    return listIds;
                default:
                    return null;
            }
        }finally {
            cursor.close();
        }
    }

    /*
     * Query only one status per UUID or per Index
     */
    public PushStatus getStatus(String  uuid) {
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
    public PushStatus getStatus(long id) {
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

    /*
     * Delete a status per ID or UUID
     */
    public boolean deleteStatus(String uuid) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, new String[]{ID, FILE_NAME}, UUID+ " = ?", new String[]{uuid}, null, null, null);
        if(cursor == null) {
            Log.d(TAG, "status not found" );
            return false;
        }
        try {
            SQLiteDatabase wd = databaseHelper.getWritableDatabase();
            if(cursor.moveToFirst() && !cursor.isAfterLast()) {

                long id = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
                String filename = cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME));

                int count = wd.delete(TABLE_NAME, ID_WHERE, new String[]{Long.toString(id)});
                if (count > 0) {
                    DatabaseFactory.getStatusTagDatabase(context).deleteEntriesMatchingStatusID(id);
                    DatabaseFactory.getStatusContactDatabase(context).deleteEntriesMatchingStatusDBID(id);
                }
                try {
                    File attachedFile = new File(FileUtil.getWritableAlbumStorageDir(), filename);
                    if (attachedFile.exists() && attachedFile.isFile())
                        attachedFile.delete();
                } catch (IOException ignore) {
                }
                return true;

            } else {
                return false;
            }
        } finally {
            cursor.close();
        }
    }

    /*
     * Update a single status or insert it if it doesn't exist
     */
    public int updateStatus(PushStatus status){
        if(status.getdbId() < 0)
            return 0;
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
        if(count > 0)
            EventBus.getDefault().post(new StatusUpdatedEvent(status));
        return count;
    }

    /*
     * Insert a single status
     */
    public long insertStatus(PushStatus status){
        ContentValues contentValues = new ContentValues();

        long contact_DBID = DatabaseFactory.getContactDatabase(context).getContactDBID(status.getAuthor().getUid());
        long sender_DBID  = DatabaseFactory.getContactDatabase(context).getContactDBID(status.receivedBy());
        long group_DBID   = DatabaseFactory.getGroupDatabase(context).getGroupDBID(status.getGroup().getGid());

        if((contact_DBID <0) || (group_DBID < 0))
            return -1;

        contentValues.put(UUID, status.getUuid());
        contentValues.put(AUTHOR_DBID, contact_DBID);
        contentValues.put(GROUP_DBID, group_DBID);
        contentValues.put(POST, status.getPost());
        contentValues.put(FILE_NAME, status.getFileName());
        contentValues.put(TIME_OF_CREATION, status.getTimeOfCreation());
        contentValues.put(TIME_OF_ARRIVAL, status.getTimeOfArrival());
        contentValues.put(TIME_TO_LIVE, status.getTTL());
        contentValues.put(SENDER_DBID, sender_DBID);
        contentValues.put(HOP_COUNT, status.getHopCount());
        contentValues.put(LIKE, status.getLike());
        contentValues.put(REPLICATION, status.getReplication());
        contentValues.put(DUPLICATE, status.getDuplicate());
        contentValues.put(USERREAD, status.hasUserReadAlready() ? 1 : 0);
        contentValues.put(USERLIKED, status.hasUserLiked() ? 1 : 0);
        contentValues.put(USERSAVED, status.hasUserSaved() ? 1 : 0);

        long statusID = databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);

        if(statusID >= 0) {
            status.setdbId(statusID);
            for (String hashtag : status.getHashtagSet()) {
                long tagID = DatabaseFactory.getHashtagDatabase(context).insertHashtag(hashtag.toLowerCase());
                if(tagID >=0 )
                    DatabaseFactory.getStatusTagDatabase(context).insertStatusTag(tagID, statusID);
            }
            EventBus.getDefault().post(new StatusInsertedEvent(status));
        }

        return statusID;
    }

    public void wipe() {
        PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
        options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_UUIDS;
        DatabaseFactory.getPushStatusDatabase(context).getStatuses(options, onWipeCallback);
    }
    DatabaseExecutor.ReadableQueryCallback onWipeCallback = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Object object) {
            if(object != null) {
                ArrayList<String> statuses = (ArrayList<String>) object;
                for(String uuid : statuses) {
                    deleteStatus(uuid);
                }
            }
            EventBus.getDefault().post(new StatusWipedEvent());
        }
    };

    /*
     * utility function to transform a row into a StatusMessage
     * ! this method does not close the cursor
     */
    private PushStatus cursorToStatus(final Cursor cursor) {
        if(cursor == null)
            return null;
        if(cursor.isAfterLast())
            return null;

        long statusDBID    = cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        long author_dbid   = cursor.getLong(cursor.getColumnIndexOrThrow(AUTHOR_DBID));
        Contact contact    = DatabaseFactory.getContactDatabase(context).getContact(author_dbid);
        long group_dbid    = cursor.getLong(cursor.getColumnIndexOrThrow(GROUP_DBID));
        Group group        = DatabaseFactory.getGroupDatabase(context).getGroup(group_dbid);
        long toc           = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_CREATION));
        String post        = cursor.getString(cursor.getColumnIndexOrThrow(POST));
        String sender_dbid = cursor.getString(cursor.getColumnIndexOrThrow(SENDER_DBID));

        PushStatus message = new PushStatus(contact, group, post, toc, sender_dbid);
        message.setdbId(statusDBID);
        message.setTimeOfArrival(cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_ARRIVAL)));
        message.setTTL(cursor.getLong(cursor.getColumnIndexOrThrow(TIME_TO_LIVE)));
        message.setFileName(cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME)));
        message.setHopCount(cursor.getInt(cursor.getColumnIndexOrThrow(HOP_COUNT)));
        message.setLike(cursor.getInt(cursor.getColumnIndexOrThrow(LIKE)));
        message.addReplication(cursor.getInt(cursor.getColumnIndexOrThrow(REPLICATION)));
        message.addDuplicate(cursor.getInt(cursor.getColumnIndexOrThrow(DUPLICATE)));
        message.setUserRead((cursor.getInt(cursor.getColumnIndexOrThrow(USERREAD)) == 1));
        message.setUserLike((cursor.getInt(cursor.getColumnIndexOrThrow(USERLIKED)) == 1));
        message.setUserSaved((cursor.getInt(cursor.getColumnIndexOrThrow(USERSAVED)) == 1));
        message.setHashtagSet(getHashTagList(statusDBID));

        return message;
    }

    private Set<String> getHashTagList(long statusID) {
        Cursor hashsetCursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            StringBuilder query = new StringBuilder(
                    "SELECT h." + HashtagDatabase.HASHTAG
                            + " FROM " + HashtagDatabase.TABLE_NAME + " h"
                            + " JOIN " + StatusTagDatabase.TABLE_NAME + " st"
                            + " ON st." + StatusTagDatabase.HDBID + " = h." + HashtagDatabase.ID
                            + " WHERE st." + StatusTagDatabase.HDBID + " = ?");
            hashsetCursor = database.rawQuery(query.toString(), new String[]{Long.toString(statusID)});
            Set<String> hashtagSet = new HashSet<String>();
            if (hashsetCursor != null) {
                for (hashsetCursor.moveToFirst(); !hashsetCursor.isAfterLast(); hashsetCursor.moveToNext()) {
                    hashtagSet.add(hashsetCursor.getString(hashsetCursor.getColumnIndexOrThrow(HashtagDatabase.HASHTAG)));
                }
            }
            return hashtagSet;
        } finally {
            if(hashsetCursor != null)
                hashsetCursor.close();
        }
    }
}
