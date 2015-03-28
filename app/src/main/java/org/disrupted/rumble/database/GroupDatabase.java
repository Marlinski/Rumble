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

    public static final String TABLE_NAME   = "group";
    public static final String ID           = "_id";
    public static final String NAME         = "name";

    public static final String DEFAULT_GROUP = "rumble.public";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID     + " INTEGER PRIMARY KEY, "
                 + NAME   + " TEXT, "
                 + "UNIQUE( " + NAME + " ) "
                 + " );";

    public GroupDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public String getGroupName(long groupID) {
        String groupName = null;
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor    = database.query(TABLE_NAME, new String[]{NAME}, ID + " = " + groupID, null, null, null, null);
        if(cursor != null)
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                groupName = cursor.getString(cursor.getColumnIndexOrThrow(GroupDatabase.NAME));

        return groupName;
    }


}
