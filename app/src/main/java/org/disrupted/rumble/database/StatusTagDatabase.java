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

/**
 * @author Lucien Loiseau
 */
public class StatusTagDatabase extends Database {

    private static final String TAG = "StatusTagDatabase";

    public  static final String TABLE_NAME = "statustag";
    public  static final String HDBID = "_hdbid";
    public  static final String SDBID = "_sdbid";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + HDBID + " INTEGER, "
                 + SDBID + " INTEGER, "
                 + " UNIQUE( " + HDBID + " , " + SDBID + "), "
                 + " FOREIGN KEY ( "+ SDBID + " ) REFERENCES " + PushStatusDatabase.TABLE_NAME   + " ( " + PushStatusDatabase.ID   + " ), "
                 + " FOREIGN KEY ( "+ HDBID + " ) REFERENCES " + HashtagDatabase.TABLE_NAME + " ( " + HashtagDatabase.ID + " )"
          + " );";

    public static final String[] CREATE_INDEXS = {
            "CREATE INDEX IF NOT EXISTS hashtag_status_id_index ON " + TABLE_NAME + " (" + SDBID + ");"
    };

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public StatusTagDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public void deleteEntriesMatchingStatusID(long statusID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, SDBID + " = ?" , new String[] {Long.toString(statusID)});
    }

    public long insertStatusTag(long tagID, long statusID){
        ContentValues contentValues = new ContentValues();
        contentValues.put(HDBID, tagID);
        contentValues.put(SDBID, statusID);

        return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

}
