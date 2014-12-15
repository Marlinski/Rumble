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
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * @author Marlinski
 */
public class StatusTagDatabase extends Database {

    private static final String TAG = "StatusTagDatabase";

    public  static final String TABLE_NAME = "statustag";
    public  static final String HID = "_id";
    public  static final String SID = "_sid";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + HID + " INTEGER, "
                 + SID + " INTEGER, "
                 + " UNIQUE( " + HID + " , " + SID + "), "
                 + " FOREIGN KEY ( "+ SID + " ) REFERENCES " + StatusDatabase.TABLE_NAME   + " ( " + StatusDatabase.ID   + " ), "
                 + " FOREIGN KEY ( "+ HID + " ) REFERENCES " + HashtagDatabase.TABLE_NAME + " ( " + HashtagDatabase.ID + " )"
          + " );";

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS hashtag_status_id_index ON " + TABLE_NAME + " (" + SID + ");"
    };

    public StatusTagDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public Cursor getStatusTag() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        return cursor;
    }

    public void deleteStatusTag(long tagID, long statusID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, HID + " = " + String.valueOf(tagID) + " and " + SID + " = " + String.valueOf(statusID) , null);
    }

    public void deleteEntriesMatchingStatusID(long statusID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, SID + " = ?" , new String[] {statusID + ""});
    }

    public long insertStatusTag(long tagID, long statusID){
        ContentValues contentValues = new ContentValues();
        contentValues.put(HID, tagID);
        contentValues.put(SID, statusID);

        return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

}
