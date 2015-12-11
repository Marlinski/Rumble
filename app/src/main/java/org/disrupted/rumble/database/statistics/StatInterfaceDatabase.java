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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import org.disrupted.rumble.database.Database;

import java.security.SecureRandom;

/**
 * @author Lucien Loiseau
 */
public class StatInterfaceDatabase extends Database {

    private static final String TAG = "InterfaceDatabase";

    public  static final String TABLE_NAME   = "interfaces";
    public  static final String ID           = "_id";
    public  static final String MACADDRESS   = "macaddress";
    public  static final String SALT         = "salt";
    public  static final String BLUETOOTH    = "bluetooth";
    public  static final String WIFI         = "wifi";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID          + " INTEGER PRIMARY KEY, "
            + SALT        + " TEXT, "
            + MACADDRESS  + " TEXT, "
            + BLUETOOTH   + " INTEGER, "
            + WIFI        + " INTEGER, "
            + "UNIQUE( "  + MACADDRESS + " ) "
            + " );";

    public StatInterfaceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public long getInterfaceDBIDFromMac(String mac) {
        Cursor cursor = null;
        try {
            SQLiteDatabase db = databaseHelper.getReadableDatabase();
            cursor = db.query(TABLE_NAME, new String[]{ID}, MACADDRESS + " = ?", new String[]{mac}, null, null, null, null);
            if (cursor != null && cursor.moveToFirst() && !cursor.isAfterLast())
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return -1;
    }

    public long insertInterface(String macAddress, boolean isBluetooth) {
        long rowid = getInterfaceDBIDFromMac(macAddress);
        if(rowid < 0) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(SALT, generateRandomSalt(10));
            contentValues.put(MACADDRESS, macAddress);
            contentValues.put(BLUETOOTH, isBluetooth);
            contentValues.put(WIFI, !isBluetooth);
            rowid = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        }
        return rowid;
    }

    private String generateRandomSalt(int length) {
        SecureRandom random = new SecureRandom();
        random.setSeed(System.currentTimeMillis());
        byte[] buf = new byte[length];
        random.nextBytes(buf);
        return Base64.encodeToString(buf,0,length,Base64.NO_WRAP);
    }


}
