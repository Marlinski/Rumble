package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import org.disrupted.rumble.database.events.GroupInsertedEvent;
import org.disrupted.rumble.message.Group;
import org.disrupted.rumble.util.AESUtil;

import java.util.ArrayList;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.SecretKeySpec;

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

    public boolean getGroups(DatabaseExecutor.ReadableQueryCallback callback){
        return DatabaseFactory.getDatabaseExecutor(context).addQuery(
                new DatabaseExecutor.ReadableQuery() {
                    @Override
                    public Object read() {
                        return getGroups();
                    }
                }, callback);
    }
    private ArrayList<Group> getGroups() {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, null, null, null, null, null);
        if(cursor == null)
            return null;
        try {
            ArrayList<Group> ret = new ArrayList<Group>();
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
                String keyEncodedBase64 = cursor.getString(cursor.getColumnIndexOrThrow(KEY));
                byte[] decodedKey = Base64.decode(keyEncodedBase64, Base64.NO_WRAP);
                SecretKey key = AESUtil.getSecretKeyFromByteArray(decodedKey);
                ret.add(new Group(name, key));
            }
            return ret;
        } finally {
            cursor.close();
        }
    }

    public Group getGroup(String groupName) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, null, NAME + " = ?", new String[] {groupName}, null, null, null);
        if(cursor == null)
            return null;

        try {
            if(cursor.moveToFirst() && !cursor.isAfterLast()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
                String keyEncodedBase64 = cursor.getString(cursor.getColumnIndexOrThrow(KEY));
                byte[] decodedKey = Base64.decode(keyEncodedBase64, Base64.NO_WRAP);
                SecretKey key = AESUtil.getSecretKeyFromByteArray(decodedKey);
                return new Group(name, key);
            } else
                return null;
        } finally {
            cursor.close();
        }
    }

    public long insertGroup(Group group){
        if(group == null)
            return 0;

        String base64EncodedKey = Base64.encodeToString(group.getGroupKey().getEncoded(), Base64.NO_WRAP);

        ContentValues contentValues = new ContentValues();
        contentValues.put(NAME, group.getName());
        contentValues.put(KEY, base64EncodedKey);

        long count = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        if(count > 0)
            EventBus.getDefault().post(new GroupInsertedEvent(group));

        return count;
    }
}
