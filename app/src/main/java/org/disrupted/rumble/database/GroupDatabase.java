package org.disrupted.rumble.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import org.disrupted.rumble.database.events.GroupDeletedEvent;
import org.disrupted.rumble.database.events.GroupInsertedEvent;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.util.CryptoUtil;

import java.util.ArrayList;
import java.util.HashSet;

import javax.crypto.SecretKey;

import de.greenrobot.event.EventBus;


/**
 * @author Lucien Loiseau
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

    public static final String CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (" + ID      + " INTEGER PRIMARY KEY, "
                 + GID     + " TEXT, "
                 + NAME    + " TEXT, "
                 + KEY     + " TEXT, "
                 + PRIVATE + " INTEGER, "
                 + DESC    + " TEXT, "
                 + "UNIQUE( " + GID +" ) "
           + " ); ";

    public GroupDatabase(Context context, SQLiteOpenHelper databaseHelper) {
        super(context, databaseHelper);
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
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

    public Group getGroup(long group_dbid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, null, ID+ " = ?", new String[] {Long.toString(group_dbid)}, null, null, null);
            if(cursor == null)
                return null;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToGroup(cursor);
            else
                return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public Group getGroup(String gid) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, null, GID+ " = ?", new String[] {gid}, null, null, null);
            if(cursor == null)
                return null;
            if(cursor.moveToFirst() && !cursor.isAfterLast())
                return cursorToGroup(cursor);
            else
                return null;
        } finally {
            if(cursor != null)
                cursor.close();
        }
    }

    public long getGroupDBID(String group_id) {
        Cursor cursor = null;
        try {
            SQLiteDatabase database = databaseHelper.getReadableDatabase();
            cursor = database.query(TABLE_NAME, new String[] { ID }, GID+ " = ?", new String[] {group_id}, null, null, null);
            if((cursor != null) && cursor.moveToFirst() && !cursor.isAfterLast())
                return cursor.getLong(cursor.getColumnIndexOrThrow(ID));
        } finally {
            if(cursor != null)
                cursor.close();
        }
        return -1;
    }


    public boolean insertGroup(Group group){
        if(group == null)
            return false;

        String base64EncodedKey = null;
        if(group.isPrivate())
            base64EncodedKey = Base64.encodeToString(group.getGroupKey().getEncoded(), Base64.NO_WRAP);

        ContentValues contentValues = new ContentValues();
        contentValues.put(NAME, group.getName());
        contentValues.put(GID, group.getGid());
        contentValues.put(KEY, base64EncodedKey);
        contentValues.put(PRIVATE, group.isPrivate() ? 1 : 0);
        contentValues.put(DESC, group.getDesc());

        long count = databaseHelper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, contentValues,SQLiteDatabase.CONFLICT_IGNORE);
        if(count > 0)
            EventBus.getDefault().post(new GroupInsertedEvent(group));
        return (count > 0);
    }

    public void deleteGroupStatus(String gid) {
        if(gid == null)
            return;

        PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
        options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_GROUP;
        options.groupIDFilters = new HashSet<String>();
        options.groupIDFilters.add(gid);
        options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_UUIDS;
        DatabaseFactory.getPushStatusDatabase(context).getStatuses(options, deleteGroupStatusCallback);
    }
    DatabaseExecutor.ReadableQueryCallback deleteGroupStatusCallback = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Object object) {
            if(object != null) {
                ArrayList<String> statuses = (ArrayList<String>) object;
                for(String uuid : statuses) {
                    DatabaseFactory.getPushStatusDatabase(context).deleteStatus(uuid);
                }
            }
        }
    };

    public void leaveGroup(String gid) {
        if(gid == null)
            return;
        deleteGroupStatus(gid);
        long groupDBID = getGroupDBID(gid);
        DatabaseFactory.getContactJoinGroupDatabase(context).deleteEntriesMatchingGroupID(groupDBID);
        if(databaseHelper.getWritableDatabase().delete(TABLE_NAME, ID+" = ?",new String[] {Long.toString(groupDBID)}) > 0)
            EventBus.getDefault().post(new GroupDeletedEvent(gid));
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
            key = CryptoUtil.getSecretKeyFromByteArray(decodedKey);
        }
        Group ret = new Group(name, gid, key);
        ret.setDesc(desc);
        return ret;
    }
}
