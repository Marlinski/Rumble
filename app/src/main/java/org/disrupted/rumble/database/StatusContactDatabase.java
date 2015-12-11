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

package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * ContactStatusDatabase keeps track of the status sent to the peers. This is to avoid
 * sending the same status twice to a contact.
 *
 * @author Lucien Loiseau
 */
public class StatusContactDatabase extends Database {

    private static final String TAG = "ContactStatusDatabase";

    public static final String TABLE_NAME     = "statuscontact";
    public static final String STATUS_DBID    = "_sdbid";
    public static final String CONTACT_DBID   = "_cdbid";


    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + STATUS_DBID     + " INTEGER, "
            + CONTACT_DBID  + " INTEGER, "
            + " UNIQUE( " + STATUS_DBID + " , " + CONTACT_DBID + "), "
            + " FOREIGN KEY ( "+ STATUS_DBID    + " ) REFERENCES " + PushStatusDatabase.TABLE_NAME  + " ( " + PushStatusDatabase.ID  + " ), "
            + " FOREIGN KEY ( "+ CONTACT_DBID + " ) REFERENCES " + ContactDatabase.TABLE_NAME   + " ( " + ContactDatabase.ID   + " ) "
            + " );";

    public StatusContactDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public void deleteEntriesMatchingStatusDBID(long statusDBID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, STATUS_DBID + " = ?" , new String[] {statusDBID + ""});
    }

    public long insertStatusContact(long statusDBID, long contactDBID){
        ContentValues contentValues = new ContentValues();
        contentValues.put(STATUS_DBID, statusDBID);
        contentValues.put(CONTACT_DBID, contactDBID);
        return databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, contentValues,SQLiteDatabase.CONFLICT_IGNORE);
    }
}
