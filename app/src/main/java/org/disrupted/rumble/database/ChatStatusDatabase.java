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

import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author Marlinski
 */
public class ChatStatusDatabase extends Database {

    private static final String TAG = "ChatDatabase";

    public static final String TABLE_NAME       = "chat_status";
    public static final  String ID               = "_id";
    public static final  String AUTHOR_NAME      = "author_name";
    public static final  String POST             = "post";        // the post itself
    public static final  String FILE_NAME        = "filename";    // the name of the attached file
    public static final  String TIME_OF_ARRIVAL  = "toa";         // time of arrival at current node
    public static final  String USERREAD         = "read";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
                 + AUTHOR_NAME + " TEXT, "
                 + POST        + " TEXT, "
                 + FILE_NAME   + " TEXT, "
                 + TIME_OF_ARRIVAL   + " INTEGER, "
                 + USERREAD    + " INTEGER "
            + " );";

    public ChatStatusDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }


}
