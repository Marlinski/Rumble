package org.disrupted.rumble.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


/**
 * @author Marlinski
 */
public class GroupDatabase  extends  Database{

    private static final String TAG = "GroupDatabase";

    public static final String TABLE_NAME   = "groups";
    public static final String ID           = "_id";
    public static final String NAME         = "name";
    public static final String KEY          = "groupkey";

    public static final String DEFAULT_GROUP = "rumble.public";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID     + " INTEGER PRIMARY KEY, "
                 + NAME   + " TEXT, "
                 + KEY    + " TEXT, "
                 + "UNIQUE( " + NAME + " ,"+ KEY +" ) "
                 + " );";

    public GroupDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

}
