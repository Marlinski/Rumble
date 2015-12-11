/*
 * Copyright (C) 2014 Lucien Loiseau
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

import org.disrupted.rumble.database.events.ChatWipedEvent;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;

import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class ChatMessageDatabase extends Database {

    private static final String TAG = "ChatMessageDatabase";

    public static final String TABLE_NAME        = "chat_message";
    public static final String ID               = "_id";
    public static final String UUID             = "uuid";
    public static final String AUTHOR_DBID      = "cdbid";       // author foreign key
    public static final String MESSAGE          = "message";     // the post itself
    public static final String FILE_NAME        = "filename";    // the name of the attached file
    public static final String TIME_OF_CREATION = "toc";         // time of creation
    public static final String TIME_OF_ARRIVAL  = "toa";         // time of arrival at current node
    public static final String PROTOCOL         = "protocol";    // the protocol from which we receive the message
    public static final String USERREAD         = "read";
    public static final String RECIPENTS        = "nb_recipients";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
                 + UUID        + " TEXT, "
                 + AUTHOR_DBID + " INTEGER, "
                 + MESSAGE     + " TEXT, "
                 + FILE_NAME   + " TEXT, "
                 + TIME_OF_CREATION  + " INTEGER, "
                 + TIME_OF_ARRIVAL   + " INTEGER, "
                 + USERREAD          + " INTEGER, "
                 + PROTOCOL          + " TEXT, "
                 + RECIPENTS         + " INTEGER, "
                 + " UNIQUE ( "+UUID+" ), "
                 + " FOREIGN KEY ( "+ AUTHOR_DBID + " ) REFERENCES " + ContactDatabase.TABLE_NAME + " ( " + ContactDatabase.ID   + " ) "
            + " );";

    public ChatMessageDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public static class ChatMessageQueryOption {
        public static final long FILTER_TOA_FROM   = 0x0001;
        public static final long FILTER_TOA_TO     = 0x0002;
        public static final long FILTER_READ       = 0x0004;

        public enum QUERY_RESULT {
            COUNT,
            LIST_OF_MESSAGE
        }

        public long         filterFlags;
        public long         to_toa;
        public long         from_toa;
        public int          answerLimit;
        public boolean      read;
        public QUERY_RESULT query_result;

        public ChatMessageQueryOption() {
            filterFlags = 0x00;
            to_toa = 0;
            from_toa = 0;
            answerLimit = 0;
            read = true;
            query_result = QUERY_RESULT.LIST_OF_MESSAGE;
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
    private Object getChatMessage(ChatMessageQueryOption options) {
        if(options == null)
            options = new ChatMessageQueryOption();


        /* 1st:  configure what the query will return */
        String select = " * ";
        switch (options.query_result) {
            case COUNT:
                select = " COUNT(*) ";
                break;
            case LIST_OF_MESSAGE:
                select = " cm.* ";
                break;
        }

        StringBuilder query = new StringBuilder(
                "SELECT "+select+" FROM "+ ChatMessageDatabase.TABLE_NAME+" cm " +
                " JOIN "+ContactDatabase.TABLE_NAME + " c" +
                " ON cm."+ChatMessageDatabase.AUTHOR_DBID+" = c."+ContactDatabase.ID
        );


        /* 2nd: we add the constraints */
        List<String> argumentList = new ArrayList<String>();
        boolean firstwhere = true;
        if(options.filterFlags > 0)
            query.append(" WHERE ( ");

        if ((options.filterFlags & ChatMessageQueryOption.FILTER_TOA_FROM) == ChatMessageQueryOption.FILTER_TOA_FROM) {
            firstwhere = false;
            query.append(" cm." + ChatMessageDatabase.TIME_OF_ARRIVAL + " > ? ");
            argumentList.add(Long.toString(options.from_toa));
        }
        if ((options.filterFlags & ChatMessageQueryOption.FILTER_TOA_TO) == ChatMessageQueryOption.FILTER_TOA_TO) {
            if(!firstwhere)
                query.append(" AND ");
            firstwhere = false;
            query.append(" cm." + ChatMessageDatabase.TIME_OF_ARRIVAL + " < ? ");
            argumentList.add(Long.toString(options.to_toa));
        }
        if((options.filterFlags & ChatMessageQueryOption.FILTER_READ) == ChatMessageQueryOption.FILTER_READ) {
            if(!firstwhere)
                query.append(" AND ");
            if(options.read)
                query.append(" cm." + ChatMessageDatabase.USERREAD + " =  1");
            else
                query.append(" cm." + ChatMessageDatabase.USERREAD + " =  0");
        }
        if(options.filterFlags > 0)
            query.append(" ) ");

        query.append(" ORDER BY " + ChatMessageDatabase.TIME_OF_ARRIVAL + " DESC");

        if(options.answerLimit > 0) {
            query.append(" LIMIT ? ");
            argumentList.add(Integer.toString(options.answerLimit));
        }

        /* perform the query
        Log.d(TAG, "[Q] query: " + query.toString());
        for(String argument : argumentList) {
            Log.d(TAG, argument+" ");
        }
        */
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.rawQuery(query.toString(),argumentList.toArray(new String[argumentList.size()]));
        if(cursor == null)
            return null;

        try {
            switch (options.query_result) {
                case COUNT:
                    cursor.moveToFirst();
                    return cursor.getInt(0);
                case LIST_OF_MESSAGE:
                    ArrayList<ChatMessage> listChatMessage = new ArrayList<ChatMessage>();
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        listChatMessage.add(cursorToChatMessage(cursor));
                    }
                    return listChatMessage;
                default:
                    return null;
            }
        } finally {
            cursor.close();
       }
    }

    public ChatMessage getChatMessage(String uuid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, null, UUID+" = ?",new String[]{uuid}, null, null, null);
            if(cursor == null)
                return null;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToChatMessage(cursor);
        } finally {
            if(cursor != null)
                cursor.close();
        }
        return null;
    }

    public long getChatMessageDBID(String uuid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, new String[] {ID}, UUID+" = ?",new String[]{uuid}, null, null, null);
            if(cursor == null)
                return -1;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
            else
                return -1;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public long insertMessage(ChatMessage chatMessage){
        ContentValues contentValues = new ContentValues();

        long contact_DBID = DatabaseFactory.getContactDatabase(context).getContactDBID(chatMessage.getAuthor().getUid());
        if(contact_DBID <0)
            return -1;

        contentValues.put(UUID,            chatMessage.getUUID());
        contentValues.put(AUTHOR_DBID,     contact_DBID);
        contentValues.put(MESSAGE,         chatMessage.getMessage());
        contentValues.put(FILE_NAME,       chatMessage.getAttachedFile());
        contentValues.put(TIME_OF_CREATION,chatMessage.getAuthorTimestamp());
        contentValues.put(TIME_OF_ARRIVAL, chatMessage.getTimestamp());
        contentValues.put(PROTOCOL,        chatMessage.getProtocolID());
        contentValues.put(USERREAD,        chatMessage.hasUserReadAlready() ? 1 : 0);
        contentValues.put(RECIPENTS,       chatMessage.getNbRecipients());

        return  databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public long updateMessage(ChatMessage chatMessage){
        ContentValues contentValues  = new ContentValues();
        contentValues.put(UUID,      chatMessage.getUUID());
        contentValues.put(USERREAD,  chatMessage.hasUserReadAlready() ? 1 : 0);
        contentValues.put(RECIPENTS, chatMessage.getNbRecipients());
        return databaseHelper.getWritableDatabase().update(TABLE_NAME, contentValues, UUID + " = ? ", new String[]{chatMessage.getUUID()});
    }

    private ChatMessage cursorToChatMessage(final Cursor cursor) {
        if(cursor == null)
            return null;
        if(cursor.isAfterLast())
            return null;

        String  uuid        = cursor.getString(cursor.getColumnIndexOrThrow(UUID));
        long author_dbid = cursor.getLong(cursor.getColumnIndexOrThrow(AUTHOR_DBID));
        String message     = cursor.getString(cursor.getColumnIndexOrThrow(MESSAGE));
        String  filename    = cursor.getString(cursor.getColumnIndexOrThrow(FILE_NAME));
        long toc = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_CREATION));
        long    toa         = cursor.getLong(cursor.getColumnIndexOrThrow(TIME_OF_ARRIVAL));
        String  protocol    = cursor.getString(cursor.getColumnIndexOrThrow(PROTOCOL));
        boolean userRead    = (cursor.getInt(cursor.getColumnIndexOrThrow(USERREAD)) == 1);
        int     recipients  = cursor.getInt(cursor.getColumnIndexOrThrow(RECIPENTS));

        Contact author   = DatabaseFactory.getContactDatabase(context).getContact(author_dbid);
        ChatMessage chatMessage = new ChatMessage(author, message, toc, toa, protocol);
        chatMessage.setUserRead(userRead);
        chatMessage.setAttachedFile(filename);
        chatMessage.setUUID(uuid);
        chatMessage.setNbRecipients(recipients);

        return chatMessage;
    }

    public void wipe() {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.delete(TABLE_NAME, null, null);
        EventBus.getDefault().post(new ChatWipedEvent());
    }
}
