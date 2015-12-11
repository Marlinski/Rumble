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
public class StatLinkLayerDatabase extends StatisticDatabase {
    private static final String TAG = "StatConnectionDatabase";

    public  static final String TABLE_NAME      = "link_layer";
    public  static final String ID              = "_id";
    public  static final String LINKLAYER_ID    = "link_layer_id";
    public  static final String TIME_STARTED    = "link_layer_start";
    public  static final String TIME_STOPPED    = "link_layer_ended";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID     + " INTEGER PRIMARY KEY, "
            + LINKLAYER_ID  + " TEXT, "
            + TIME_STARTED + " INTEGER, "
            + TIME_STOPPED   + " INTEGER "
            + " );";

    public StatLinkLayerDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public long insertLinkLayerStat(String linkLayerID, long started_nano, long stopped_nano) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(LINKLAYER_ID, linkLayerID);
        contentValues.put(TIME_STARTED, started_nano);
        contentValues.put(TIME_STOPPED, stopped_nano);
        return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

    public void clean() {
        databaseHelper.getWritableDatabase().delete(TABLE_NAME, null, null);
    }

}
