/*
 * Copyright (C) 2014 Lucien Loiseau
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

package org.disrupted.rumble.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.content.CursorLoader;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.objects.PushStatus;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * From https://developer.android.com/training/basics/data-storage/files.html
 * @author Lucien Loiseau
 */
public class FileUtil {

    private static final String TAG = "FileUtil";
    public static String RUMBLE_IMAGE_ALBUM_NAME = "Rumble";

    public static String cleanBase64(String uuid) {
        String ret = uuid.replace('/', '_');
        ret = ret.replace('+','-');
        ret = ret.replaceAll("[^a-zA-Z0-9_-]", "");
        return ret;
    }

    public static boolean isFileNameClean(String input) {
        String nameWithoutExtension = input.substring(0, input.lastIndexOf('.'));
        return (nameWithoutExtension.equals(nameWithoutExtension.replaceAll("[^a-zA-Z0-9_-]", "")));
    }

    private static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    private static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static File getWritableAlbumStorageDir() throws IOException {
        if(!isExternalStorageWritable())
            throw  new IOException("Storage is not writable");

        File file = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                RUMBLE_IMAGE_ALBUM_NAME);
        file.mkdirs();

        if(file.getFreeSpace() < PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE)
            throw  new IOException("not enough space available ("+file.getFreeSpace()+"/"+PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE+")");

        return file;
    }

    public static File getReadableAlbumStorageDir() throws IOException {
        if(!isExternalStorageReadable())
            throw  new IOException("Storage is not readable");

        File file = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                RUMBLE_IMAGE_ALBUM_NAME);
        if (!file.mkdirs()) {
        }

        return file;
    }

    @SuppressLint("NewApi")
    private static String getRealPathFromURI_API19(Context context, Uri uri){
        String filePath = "";
        String wholeID = DocumentsContract.getDocumentId(uri);

        // Split at colon, use second item in the array
        String id = wholeID.split(":")[1];

        String[] column = { MediaStore.Images.Media.DATA };

        // where id is equal to
        String sel = MediaStore.Images.Media._ID + "=?";

        Cursor cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                column, sel, new String[]{ id }, null);

        int columnIndex = cursor.getColumnIndex(column[0]);

        if (cursor.moveToFirst()) {
            filePath = cursor.getString(columnIndex);
        }
        cursor.close();
        return filePath;
    }


    @SuppressLint("NewApi")
    private static String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        String result = null;

        CursorLoader cursorLoader = new CursorLoader(
                context,
                contentUri, proj, null, null, null);
        Cursor cursor = cursorLoader.loadInBackground();

        if(cursor != null){
            int column_index =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            result = cursor.getString(column_index);
        }
        return result;
    }

    private static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri){
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index
                = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        return cursor.getString(column_index);
    }

    public static String getRealPathFromURI(Context context, Uri contentUri) {
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion >= Build.VERSION_CODES.KITKAT)
            return getRealPathFromURI_API19(context, contentUri);
        if (currentapiVersion >= Build.VERSION_CODES.HONEYCOMB)
            return getRealPathFromURI_API11to18(context, contentUri);
        return getRealPathFromURI_BelowAPI11(context, contentUri);
    }

}
