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

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * @author Marlinski
 */
public class FileDatabase extends Database {

    private static final String TAG = "FileDatabase";

    public  static final String TABLE_NAME    = "file";
    public  static final String ID            = "_id";
    public  static final String FILE_HASH     = "hash";
    public  static final String FILE_PATH     = "path";
    public  static final String SIZE          = "size";
    public  static final String MIME_TYPE     = "mime";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID +  " INTEGER PRIMARY KEY, "
                 + FILE_HASH + " TEXT, "
                 + FILE_PATH + " TEXT, "
                 + SIZE      + " INTEGER, "
                 + MIME_TYPE + " TEXT, "
                 + "UNIQUE(" + FILE_HASH + "), "
                 + "UNIQUE(" + FILE_PATH + ")"
          + " );";

    public FileDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public Cursor getFiles() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor           = database.query(TABLE_NAME, null, null, null, null, null, null);

        return cursor;
    }



}
