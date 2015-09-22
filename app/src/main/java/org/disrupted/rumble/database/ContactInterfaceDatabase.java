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

import org.disrupted.rumble.database.objects.Contact;

import java.util.HashSet;
import java.util.Set;

/**
 * ContactInterfaceDatabase keeps track of the interfaces we met that match a certain contact
 * This is to avoid resending all of the contact information (name, uid, avatar, etc.) whenever
 * we reconnect to a device.
 *
 * In order to avoid storing all the interface mac address in plain text, we store a hash of the
 * mac address.
 *
 * @author Marlinski
 */
public class ContactInterfaceDatabase extends Database {


    private static final String TAG = "ContactInterfaceDatabase";

    public static final String TABLE_NAME       = "statusinterface";
    public static final String CONTACT_DBID     = "_cdbid";
    public static final String INTERFACE_DBID   = "_idbid";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + CONTACT_DBID     + " INTEGER, "
                 + INTERFACE_DBID  + " INTEGER, "
                 + " UNIQUE( " + INTERFACE_DBID + "), "
                 + " FOREIGN KEY ( "+ CONTACT_DBID    + " ) REFERENCES " + ContactDatabase.TABLE_NAME  + " ( " + ContactDatabase.ID  + " ), "
                 + " FOREIGN KEY ( "+ INTERFACE_DBID + " ) REFERENCES " + InterfaceDatabase.TABLE_NAME   + " ( " + InterfaceDatabase.ID   + " ) "
            + " );";

    public ContactInterfaceDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public void deleteEntriesMatchingContactDBID(long contactDBID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, CONTACT_DBID + " = ?" , new String[] {contactDBID + ""});
    }

    public long insertContactInterface(long contactDBID, long interfaceDBID){
        ContentValues contentValues = new ContentValues();
        contentValues.put(CONTACT_DBID, contactDBID);
        contentValues.put(INTERFACE_DBID, interfaceDBID);
        return databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, contentValues,SQLiteDatabase.CONFLICT_REPLACE);
    }

}
