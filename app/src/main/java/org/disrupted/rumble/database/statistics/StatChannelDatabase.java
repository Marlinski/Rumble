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

/**
 * @author Lucien Loiseau
 */
public class StatChannelDatabase extends StatisticDatabase {

    private static final String TAG = "StatConnectionDatabase";

    public  static final String TABLE_NAME      = "connection";
    public  static final String ID              = "_id";
    public  static final String IFACE_DBID      = "interface_db_id";
    public  static final String IFACE_TYPE      = "interface_type";
    public  static final String CONNECTED       = "connected";
    public  static final String DISCONNECTED    = "disconnected";
    public  static final String PROTOCOL        = "protocol";
    public  static final String BYTES_RECEIVED  = "bytes_received";
    public  static final String IN_TRANS_TIME   = "in_transmission_time";
    public  static final String BYTES_SENT      = "bytes_sent";
    public  static final String OUT_TRANS_TIME  = "out_transmission_time";
    public  static final String STATUS_RECEIVED = "nb_status_received";
    public  static final String STATUS_SENT     = "nb_status_sent";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID     + " INTEGER PRIMARY KEY, "
            + IFACE_DBID + " INTEGER, "
            + IFACE_TYPE + " TEXT, "
            + CONNECTED       + " INTEGER, "
            + DISCONNECTED    + " INTEGER, "
            + PROTOCOL        + " TEXT, "
            + BYTES_RECEIVED  + " INTEGER, "
            + IN_TRANS_TIME   + " INTEGER, "
            + BYTES_SENT      + " INTEGER, "
            + OUT_TRANS_TIME  + " INTEGER, "
            + STATUS_RECEIVED + " INTEGER, "
            + STATUS_SENT     + " INTEGER, "
            + " FOREIGN KEY ( "+ IFACE_DBID + " ) REFERENCES " + StatInterfaceDatabase.TABLE_NAME  + " ( " + StatInterfaceDatabase.ID  + " ) "
            + " );";

    public StatChannelDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public long insertChannelStat(long iface_dbid, String iface_type, long connected_nano,
                                  long disconnected_nano, String protocolID, long bytes_rcvd,
                                  long in_trans_time, long bytes_sent, long out_trans_time,
                                  int status_received, int status_sent) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(IFACE_DBID,  iface_dbid);
        contentValues.put(IFACE_TYPE,  iface_type);
        contentValues.put(CONNECTED,   connected_nano);
        contentValues.put(DISCONNECTED, disconnected_nano);
        contentValues.put(PROTOCOL, protocolID);
        contentValues.put(BYTES_RECEIVED, bytes_rcvd);
        contentValues.put(IN_TRANS_TIME, in_trans_time);
        contentValues.put(BYTES_SENT, bytes_sent);
        contentValues.put(OUT_TRANS_TIME, out_trans_time);
        contentValues.put(STATUS_RECEIVED, status_received);
        contentValues.put(STATUS_SENT, status_sent);
        return databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
    }

    public void clean() {
        databaseHelper.getWritableDatabase().delete(TABLE_NAME,null,null);
    }

}
