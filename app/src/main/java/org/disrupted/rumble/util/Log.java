/*
 * Copyright (C) 2014 Lucien Loiseau
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

import org.disrupted.rumble.app.RumbleApplication;

/**
 * @author Lucien Loiseau
 */
public class Log {

    public static void d(String tag, String message) {
        if(RumblePreferences.isLogcatDebugEnabled(RumbleApplication.getContext()))
            android.util.Log.d(tag,message);
    }

    public static void d(String tag, String message, Throwable t) {
        if(RumblePreferences.isLogcatDebugEnabled(RumbleApplication.getContext()))
            android.util.Log.d(tag,message,t);
    }

    public static void e(String tag, String message) {
        if(RumblePreferences.isLogcatDebugEnabled(RumbleApplication.getContext()))
            android.util.Log.e(tag,message);
    }

    public static void e(String tag, String message, Throwable t) {
        if(RumblePreferences.isLogcatDebugEnabled(RumbleApplication.getContext()))
            android.util.Log.e(tag,message,t);
    }
}
