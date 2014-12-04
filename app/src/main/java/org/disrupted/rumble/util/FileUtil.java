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
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;

import java.io.File;

/**
 * From https://developer.android.com/training/basics/data-storage/files.html
 * @author Marlinski
 */
public class FileUtil {

    private static final String TAG = "FileUtil";

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static File getWritableAlbumStorageDir(long size) {
        if(!isExternalStorageWritable())
            return null;

        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), RumbleApplication.RUMBLE_IMAGE_ALBUM_NAME);
        if (!file.mkdirs()) {
        }

        if(file.getFreeSpace() < size)
            return null;

        if(file.getFreeSpace() < RumbleApplication.MINIMUM_FREE_SPACE_AVAILABLE)
            return null;

        return file;
    }

    public static File getReadableAlbumStorageDir() {
        if(!isExternalStorageReadable())
            return null;

        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES),  RumbleApplication.RUMBLE_IMAGE_ALBUM_NAME);
        if (!file.mkdirs()) {
        }

        return file;
    }

}
