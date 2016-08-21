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

import android.content.Context;
import android.preference.PreferenceManager;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;

/**
 * @author Lucien Loiseau
 */
public class RumblePreferences {

    public static final String PREF_START_ON_BOOT       = "start_on_boot";
    public static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";
    public static final String PREF_USER_OK_SYNC        = "ok_sync";
    public static final String PREF_LOGCAT_DEBUG        = "logcat_debug";
    public static final String USER_ANONYMOUS_ID        = "anonymous_id";
    public static final String LAST_SYNC                = "last_sync";
    private static final int   SYNC_EVERY               = 3600*24*1000;

    public static String getAnonymousID(Context context) {
        String id = getStringPreference(context, USER_ANONYMOUS_ID,"");
        if(id.equals("")) {
            createAnonymousID(context);
            id = getStringPreference(context, USER_ANONYMOUS_ID,"");
        }
        return id;
    }
    private static void createAnonymousID(Context context) {
        setStringPreference(context, USER_ANONYMOUS_ID, HashUtil.generateRandomString(20));
    }

    public static boolean startOnBoot(Context context) {
        return getBooleanPreference(context, PREF_START_ON_BOOT, false);
    }
    public static void setStartOnBoot(Context context, Boolean bool) {
        setBooleanPreference(context, PREF_START_ON_BOOT, bool);
    }

    public static boolean hasUserLearnedDrawer(Context context) {
        return getBooleanPreference(context, PREF_USER_LEARNED_DRAWER, false);
    }
    public static void setUserLearnedDrawer(Context context, Boolean bool) {
        setBooleanPreference(context, PREF_USER_LEARNED_DRAWER, bool);
    }

    public static boolean UserOkWithSharingAnonymousData(Context context) {
        return getBooleanPreference(context, PREF_USER_OK_SYNC, false);
    }
    public static void setUserPreferenceWithSharingData(Context context, Boolean bool) {
        setBooleanPreference(context, PREF_USER_OK_SYNC, bool);
    }

    public static boolean isLogcatDebugEnabled(Context context) {
        return getBooleanPreference(context, PREF_LOGCAT_DEBUG, false);
    }
    public static void setLogcatDebugging(Context context, Boolean bool) {
        setBooleanPreference(context, PREF_LOGCAT_DEBUG, bool);
    }

    public static boolean isTimeToSync(Context context) {
        long last = getLongPreference(context, LAST_SYNC, 0);
        return ((System.currentTimeMillis() - last) > SYNC_EVERY);
    }
    public static void updateLastSync(Context context) {
        setLongPreference(context, LAST_SYNC, System.currentTimeMillis());
    }

    /**
     *  Shared Preferences Setter and Getter
     */
    private static void setBooleanPreference(Context context, String key, boolean value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(key, value).commit();
    }

    private static boolean getBooleanPreference(Context context, String key, boolean defaultValue) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(key, defaultValue);
    }

    public static void setStringPreference(Context context, String key, String value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(key, value).commit();
    }

    private static String getStringPreference(Context context, String key, String defaultValue) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(key, defaultValue);
    }

    private static int getIntegerPreference(Context context, String key, int defaultValue) {
        return PreferenceManager.getDefaultSharedPreferences(context).getInt(key, defaultValue);
    }

    private static void setIntegerPrefrence(Context context, String key, int value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putInt(key, value).commit();
    }

    private static long getLongPreference(Context context, String key, long defaultValue) {
        return PreferenceManager.getDefaultSharedPreferences(context).getLong(key, defaultValue);
    }

    private static void setLongPreference(Context context, String key, long value) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(key, value).commit();
    }

}
