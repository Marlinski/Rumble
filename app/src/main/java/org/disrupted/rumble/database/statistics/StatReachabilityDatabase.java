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

package org.disrupted.rumble.database.statistics;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteOpenHelper;

import org.disrupted.rumble.database.Database;

/**
 * @author Lucien Loiseau
 */
public class StatReachabilityDatabase extends StatisticDatabase {

    private static final String TAG = "NeighbourReachabilityDatabase";

    public  static final String TABLE_NAME  = "reachability";
    public  static final String ID          = "_id";
    public  static final String IFACED_BID  = "interface_db_id";
    public  static final String TIMESTAMP   = "timestamp";
    public  static final String REACHABLE   = "reachable";
    public  static final String UNREACHABLE = "unreachable";
    public  static final String DURATION    = "encounter_duration";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID     + " INTEGER PRIMARY KEY, "
            + IFACED_BID  + " INTEGER, "
            + TIMESTAMP   + " INTEGER, "
            + REACHABLE   + " INTEGER, "
            + UNREACHABLE + " INTEGER, "
            + DURATION    + " INTEGER, "
            + " FOREIGN KEY ( "+ IFACED_BID + " ) REFERENCES " + StatInterfaceDatabase.TABLE_NAME  + " ( " + StatInterfaceDatabase.ID  + " ) "
            + " );";

    public StatReachabilityDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public long insertReachability(long iface_dbid, long timestamp_nano, boolean reachable, long duration) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(IFACED_BID,  iface_dbid);
        contentValues.put(TIMESTAMP,   timestamp_nano);
        contentValues.put(REACHABLE,   reachable);
        contentValues.put(UNREACHABLE, !reachable);
        contentValues.put(DURATION,    duration);
        return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

    public void clean() {
        databaseHelper.getWritableDatabase().delete(TABLE_NAME, null, null);
    }
}
