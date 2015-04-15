package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import org.disrupted.rumble.database.events.GroupInsertedEvent;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.util.AESUtil;

import java.util.ArrayList;

import javax.crypto.SecretKey;

import de.greenrobot.event.EventBus;


/**
 * @author Marlinski
 */
public class GroupDatabase  extends  Database{

    private static final String TAG = "GroupDatabase";

    public static final String TABLE_NAME   = "groups";
    public static final String ID           = "_id";
    public static final String GID          = "gid";
    public static final String NAME         = "name";
    public static final String KEY          = "groupkey";
    public static final String PRIVATE      = "private";
    public static final String DESC         = "desc";

    public static final String DEFAULT_PUBLIC_GROUP = "rumble.public";

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID      + " INTEGER PRIMARY KEY, "
                 + GID     + " TEXT, "
                 + NAME    + " TEXT, "
                 + KEY     + " TEXT, "
                 + PRIVATE + " INTEGER, "
                 + DESC    + " TEXT, "
                 + "UNIQUE( " + NAME + " ,"+ KEY +" ) "
           + " ); ";

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
                ret.add(cursorToGroup(cursor));
            }
            return ret;
        } finally {
            cursor.close();
        }
    }

    public long insertGroup(Group group){
        if(group == null)
            return 0;

        String base64EncodedKey = null;
        if(group.isIsprivate())
            base64EncodedKey = Base64.encodeToString(group.getGroupKey().getEncoded(), Base64.NO_WRAP);

        ContentValues contentValues = new ContentValues();
        contentValues.put(NAME, group.getName());
        contentValues.put(GID, group.getGid());
        contentValues.put(KEY, base64EncodedKey);
        contentValues.put(PRIVATE, group.isIsprivate() ? 1 : 0);
        contentValues.put(DESC, group.getDesc());

        long count = databaseHelper.getWritableDatabase().insert(TABLE_NAME, null, contentValues);
        if(count > 0)
            EventBus.getDefault().post(new GroupInsertedEvent(group));

        return count;
    }


    private Group cursorToGroup(Cursor cursor) {
        if(cursor == null)
            return null;

        String name   = cursor.getString(cursor.getColumnIndexOrThrow(NAME));
        String gid    = cursor.getString(cursor.getColumnIndexOrThrow(GID));
        boolean isPrivate = (cursor.getInt(cursor.getColumnIndexOrThrow(PRIVATE)) == 1);
        String desc   = cursor.getString(cursor.getColumnIndexOrThrow(DESC));

        SecretKey key = null;
        if(isPrivate) {
            String keyEncodedBase64 = cursor.getString(cursor.getColumnIndexOrThrow(KEY));
            byte[] decodedKey = Base64.decode(keyEncodedBase64, Base64.NO_WRAP);
            key = AESUtil.getSecretKeyFromByteArray(decodedKey);
        }
        Group ret = new Group(name, gid, key);
        ret.setDesc(desc);
        return ret;
    }
}
