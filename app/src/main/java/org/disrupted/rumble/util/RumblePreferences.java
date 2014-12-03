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

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * @author Marlinski
 */
public class RumblePreferences {

    public  static final String IDENTITY_PREF            = "pref_choose_identity";
    public  static final String ENABLE_BLUETOOTH_PREF    = "pref_use_bluetooth";
    public  static final String ENABLE_WIFI_PREF         = "pref_use_wifi";
    public  static final String ENABLE_FIRECHAT_PREF     = "pref_enable_firechat";
    public  static final String PREF_USER_LEARNED_DRAWER = "navigation_drawer_learned";

    public static String getIdentityContactUri(Context context) {
        return getStringPreference(context, IDENTITY_PREF, null);
    }

    public static void setIdentityContactUri(Context context, String identityUri) {
               setStringPreference(context, IDENTITY_PREF, identityUri);
    }

    public static Boolean isBluetoothEnable(Context context) {
        return getBooleanPreference(context, ENABLE_BLUETOOTH_PREF, false);
    }

    public static void setBluetoothDisable(Context context, Boolean disable) {
               setBooleanPreference(context, ENABLE_BLUETOOTH_PREF, disable);
    }

    public static Boolean isWifiEnable(Context context) {
        return getBooleanPreference(context, ENABLE_WIFI_PREF, false);
    }

    public static void setWifiDisable(Context context, Boolean disable) {
               setBooleanPreference(context, ENABLE_WIFI_PREF, disable);
    }

    public static Boolean isFirechatEnable(Context context) {
        return getBooleanPreference(context, ENABLE_FIRECHAT_PREF, true);
    }

    public static void setFirechatDisable(Context context, Boolean disable) {
               setBooleanPreference(context, ENABLE_WIFI_PREF, disable);
    }

    public static boolean hasUserLearnedDrawer(Context context) {
        return getBooleanPreference(context, PREF_USER_LEARNED_DRAWER, false);
    }

    public static void setUserLearnedDrawer(Context context, Boolean bool) {
        setBooleanPreference(context, PREF_USER_LEARNED_DRAWER, bool);
    }

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
