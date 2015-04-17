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
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * ForwarderDatabase keeps track of those who already received or sent a status.
 * Because we may receive status from a neighbour before he actually gives us
 * his name, we keep track of a hashed ID of his mac address.
 *
 * In the case of Bluetooth, the default ID is hash(MacAddress, DeviceName)
 * In the case of WiFi, the default ID is hash(MacAddress, UserName)
 *
 * If in the future we eventually get the neighbour's name, we simply add an alias
 * for his ID hash(MacAddress, name) to the AliasDatabase
 *
 * @author Marlinski
 */
public class StatusInterfaceDatabase extends Database {


    private static final String TAG = "ForwarderDatabase";

    public static final String TABLE_NAME       = "statusinterface";
    public static final String STATUS_DBID      = "_sdbid";
    public static final String INTERFACE_DBID   = "_idbid";


    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + STATUS_DBID     + " INTEGER, "
                 + INTERFACE_DBID  + " INTEGER, "
                 + " UNIQUE( " + STATUS_DBID + " , " + INTERFACE_DBID + "), "
                 + " FOREIGN KEY ( "+ STATUS_DBID    + " ) REFERENCES " + PushStatusDatabase.TABLE_NAME  + " ( " + PushStatusDatabase.ID  + " ), "
                 + " FOREIGN KEY ( "+ INTERFACE_DBID + " ) REFERENCES " + InterfaceDatabase.TABLE_NAME   + " ( " + InterfaceDatabase.ID   + " ) "
            + " );";

    public StatusInterfaceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public void deleteEntriesMatchingStatusDBID(long statusDBID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, STATUS_DBID + " = ?" , new String[] {statusDBID + ""});
    }

    public long insertStatusInterface(long statusDBID, long interfaceDBID){
        ContentValues contentValues = new ContentValues();
        contentValues.put(STATUS_DBID, statusDBID);
        contentValues.put(INTERFACE_DBID, interfaceDBID);
        return databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, contentValues,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
