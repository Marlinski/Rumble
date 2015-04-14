package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.disrupted.rumble.database.events.GroupInsertedEvent;
import org.disrupted.rumble.message.Group;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;


/**
 * @author Marlinski
 */
public class GroupDatabase  extends  Database{

    private static final String TAG = "GroupDatabase";

    public static final String TABLE_NAME   = "groups";
    public static final String ID           = "_id";
    public static final String NAME         = "name";
    public static final String KEY          = "groupkey";
    public static final String PRIVATE      = "private";

    public static final String DEFAULT_GROUP = "rumble.public";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID      + " INTEGER PRIMARY KEY, "
                 + NAME    + " TEXT, "
                 + KEY     + " TEXT, "
                 + PRIVATE + " INTEGER, "
                 + "UNIQUE( " + NAME + " ,"+ KEY +" ) "
           + " );";

    public GroupDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    public boolean getGroupNames(DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Object read() {
                        return getGroupNames();
                    }
                }, callback);
    }
    private ArrayList<String> getGroupNames() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        if(cursor == null)
            return null;
        try {
            ArrayList<String> ret = new ArrayList<String>();
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                ret.add(cursor.getString(cursor.getColumnIndexOrThrow(NAME)));
            }
            return ret;
        } finally {
            cursor.close();
        }
    }

    public long insertGroup(Group group){
        if(group == null)
            return 0;
        ContentValues contentValues = new ContentValues();
        contentValues.put(NAME, group.getName());
        contentValues.put(KEY, group.getGroupkey());
        contentValues.put(PRIVATE, group.isPrivate() ? 1 : 0);

        long count = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        if(count > 0)
            EventBus.getDefault().post(new GroupInsertedEvent(group));

        return count;
    }
}
