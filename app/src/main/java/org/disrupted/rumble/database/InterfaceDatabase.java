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

/**
 * @author Marlinski
 */
public class InterfaceDatabase extends Database {

    private static final String TAG = "InterfaceDatabase";


    public  static final String TABLE_NAME    = "interface";
    public  static final String ID            = "_id";
    public  static final String INTERFACE     = "interfaceID";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID         + " INTEGER PRIMARY KEY, "
                 + INTERFACE  + " TEXT, "
                 + "UNIQUE( " + INTERFACE + " ) "
            + " );";

    public InterfaceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public long getInterfaceDBID(String interfaceID) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = databaseHelper.getReadableDatabase();
            cursor = db.query(TABLE_NAME, new String[]{ID}, INTERFACE + " = ?", new String[]{interfaceID}, null, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isAfterLast())
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return -1;
    }
    public long insertInterface(String interfaceID) {
        long rowid = getInterfaceDBID(interfaceID);
        if(rowid < 0) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(INTERFACE, interfaceID);
            databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        }
        return rowid;
    }

}
