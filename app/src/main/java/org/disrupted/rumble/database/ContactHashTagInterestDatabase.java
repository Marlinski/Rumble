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
 * @author Lucien Loiseau
 */
public class ContactHashTagInterestDatabase extends Database {
    private static final String TAG = "ContactInterestTagDatabase";

    public  static final String TABLE_NAME = "contact_hashtag_interest";
    public  static final String CDBID = "_udbid";
    public  static final String HDBID = "_hdbid";
    public  static final String INTEREST = "interest";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + CDBID + " INTEGER, "
                 + HDBID + " INTEGER, "
                 + INTEREST + " INTEGER, "
                 + " UNIQUE( " + CDBID + " , " + HDBID + "), "
                 + " FOREIGN KEY ( "+ CDBID + " ) REFERENCES " + ContactDatabase.TABLE_NAME   + " ( " + ContactDatabase.ID   + " ), "
                 + " FOREIGN KEY ( "+ HDBID + " ) REFERENCES " + GroupDatabase.TABLE_NAME + " ( " + HashtagDatabase.ID + " )"
            + " );";


    public ContactHashTagInterestDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    public void deleteEntriesMatchingContactID(long contactID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, CDBID + " = ?" , new String[] {Long.toString(contactID)});
    }

    public void deleteContactTagInterest(long contactDBID, long hashtagDBID){
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        db.delete(TABLE_NAME, CDBID + " = ? AND "+HDBID + " = ? " ,
                new String[] {Long.toString(contactDBID), Long.toString(hashtagDBID)});
    }



    public long insertContactTagInterest(long contactID, long hashtagID, int value){
        ContentValues contentValues = new ContentValues();
        contentValues.put(CDBID, contactID);
        contentValues.put(HDBID, hashtagID);
        contentValues.put(INTEREST, value);
        SQLiteDatabase db = databaseHelper.getWritableDatabase();
        return db.insertWithOnConflict(TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE);
    }
}
