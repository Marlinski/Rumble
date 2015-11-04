/*
 * Copyright (C) 2014 Disrupted Systems
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

package org.disrupted.rumble.util;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.disrupted.rumble.app.RumbleApplication;

/**
 * @author Marlinski
 */
public class SharedPreferenceUtil {

    public static final String OK_SYNC   = "ok_sync";
    public static final String LAST_SYNC = "last_sync";
    private static final int   SYNC_EVERY_DAY = 3600*24*1000;

    public static boolean UserOkWithSharingAnonymousData() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(RumbleApplication.getContext());
        return prefs.getBoolean(OK_SYNC, true);
    }

    public static boolean isTimeToSync() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(RumbleApplication.getContext());
        long last = prefs.getLong(LAST_SYNC,0);
        return ((System.currentTimeMillis() - last) > SYNC_EVERY_DAY);
    }
}
