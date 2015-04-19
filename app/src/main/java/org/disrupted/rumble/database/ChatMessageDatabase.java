/*
 * Copyright (C) 2014 Disrupted Systems
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.disrupted.rumble.database.events.ChatMessageInsertedEvent;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class ChatMessageDatabase extends Database {

    private static final String TAG = "ChatMessageDatabase";

    public static final String TABLE_NAME        = "chat_message";
    public static final  String ID               = "_id";
    public static final  String AUTHOR_DBID      = "cdbid";       // author foreign key
    public static final  String MESSAGE          = "message";     // the post itself
    public static final  String FILE_NAME        = "filename";    // the name of the attached file
    public static final  String TIME_OF_ARRIVAL  = "toa";         // time of arrival at current node
    public static final  String USERREAD         = "read";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
                 + AUTHOR_DBID + " INTEGER, "
                 + MESSAGE     + " TEXT, "
                 + FILE_NAME   + " TEXT, "
                 + TIME_OF_ARRIVAL   + " INTEGER, "
                 + USERREAD          + " INTEGER, "
                 + "FOREIGN KEY ( "+ AUTHOR_DBID + " ) REFERENCES " + ContactDatabase.TABLE_NAME + " ( " + ContactDatabase.ID   + " ) "
            + " );";

    public ChatMessageDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }


    public static class ChatMessageQueryOption {
        public static final long FILTER_TOA_FROM   = 0x0001;
        public static final long FILTER_TOA_TO     = 0x0002;

        public long         filterFlags;
        public long         to_toa;
        public long         from_toa;
        public int          answerLimit;

        public ChatMessageQueryOption() {
            filterFlags = 0x00;
            to_toa = 0;
            from_toa = 0;
            answerLimit = 0;
        }
    }



    public boolean getChatMessage(final ChatMessageQueryOption options, DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Object read() {
                        return getChatMessage(options);
                    }
                }, callback);
    }
    private ArrayList<ChatMessage> getChatMessage(ChatMessageQueryOption options) {
        if(options == null)
            options = new ChatMessageQueryOption();

        /* first we build the select */
        StringBuilder query = new StringBuilder(
                "SELECT * FROM "+ ChatMessageDatabase.TABLE_NAME+" cm " +
                " JOIN "+ContactDatabase.TABLE_NAME + " c" +
                " ON cm."+ChatMessageDatabase.AUTHOR_DBID+" = c."+ContactDatabase.ID
        );

        List<String> argumentList = new ArrayList<String>();
        if(options.filterFlags > 0)
            query.append(" WHERE ( ");
        /* then the constraints */
        if ((options.filterFlags & ChatMessageQueryOption.FILTER_TOA_FROM) == ChatMessageQueryOption.FILTER_TOA_FROM) {
            query.append(" cm." + ChatMessageDatabase.TIME_OF_ARRIVAL + " > ? ");
            argumentList.add(Long.toString(options.from_toa));
        }
        if ((options.filterFlags & ChatMessageQueryOption.FILTER_TOA_TO) == ChatMessageQueryOption.FILTER_TOA_TO) {
            query.append(" AND cm." + ChatMessageDatabase.TIME_OF_ARRIVAL + " < ? ");
            argumentList.add(Long.toString(options.to_toa));
        }
        if(options.filterFlags > 0)
            query.append(" ) ");

        query.append(" ORDER BY " + ChatMessageDatabase.TIME_OF_ARRIVAL);

        if(options.answerLimit > 0) {
            query.append(" LIMIT ? ");
            argumentList.add(Integer.toString(options.answerLimit));
        }

        /* perform the query */
        Log.d(TAG, "[Q] query: " + query.toString());
        for(String argument : argumentList) {
            Log.d(TAG, argument+" ");
        }
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery(query.toString(),argumentList.toArray(new String[argumentList.size()]));
        if(cursor == null)
            return null;

        ArrayList<ChatMessage> listChatMessage = new ArrayList<ChatMessage>();
        for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
            listChatMessage.add(cursorToChatMessage(cursor));
        }
        return listChatMessage;
    }

    public long insertMessage(ChatMessage chatMessage){
        ContentValues contentValues = new ContentValues();

        long contact_DBID = DatabaseFactory.getContactDatabase(context).getContactDBID(chatMessage.getContact().getUid());
        if(contact_DBID <0)
            return -1;

        contentValues.put(AUTHOR_DBID,     contact_DBID);
        contentValues.put(MESSAGE,         chatMessage.getMessage());
        contentValues.put(FILE_NAME,       chatMessage.getAttachedFile());
        contentValues.put(TIME_OF_ARRIVAL, chatMessage.getTimeOfArrival());
        contentValues.put(USERREAD,        chatMessage.hasUserReadAlready() ? 1 : 0);

        long statusID = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);

        if(statusID >= 0)
            EventBus.getDefault().post(new ChatMessageInsertedEvent(chatMessage));

        return statusID;
    }


    private ChatMessage cursorToChatMessage(final Cursor cursor) {
        if(cursor == null)
            return null;
        if(cursor.isAfterLast())
            return null;

        long contact_dbid = cursor.getLong(cursor.getColumnIndexOrThrow(AUTHOR_DBID));
        Contact contact  = DatabaseFactory.getContactDatabase(context).getContact(contact_dbid);
        long toa         = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_ARRIVAL));
        String message   = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE));

        ChatMessage chatMessage = new ChatMessage(contact, message, toa);
        chatMessage.setUserRead(cursor.getInt(cursor.getColumnIndexOrThrow(USERREAD)) == 1);

        return chatMessage;
    }
}
