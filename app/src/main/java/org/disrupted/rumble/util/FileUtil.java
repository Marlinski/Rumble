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

package org.disrupted.rumble.util;

import android.os.Environment;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.objects.PushStatus;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * From https://developer.android.com/training/basics/data-storage/files.html
 * @author Marlinski
 */
public class FileUtil {

    private static final String TAG = "FileUtil";

    public static String cleanBase64(String uuid) {
        String ret = uuid.replace('/','_');
        ret = ret.replace('+','-');
        ret = ret.replaceAll("[^a-zA-Z0-9_-]", "");
        return ret;
    }

    public static boolean isFileNameClean(String input) {
        return (input.equals(input.replaceAll("[^a-zA-Z0-9_-]", "")));
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

        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), RumbleApplication.RUMBLE_IMAGE_ALBUM_NAME);
        file.mkdirs();

        if(file.getFreeSpace() < PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE)
            throw  new IOException("not enough space available ("+file.getFreeSpace()+"/"+PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE+")");

        return file;
    }

    public static File getReadableAlbumStorageDir() throws IOException {
        if(!isExternalStorageReadable())
            throw  new IOException("Storage is not readable");

        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES),  RumbleApplication.RUMBLE_IMAGE_ALBUM_NAME);
        if (!file.mkdirs()) {
        }

        return file;
    }

}
