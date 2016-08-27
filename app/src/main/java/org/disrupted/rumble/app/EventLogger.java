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

package org.disrupted.rumble.app;

import android.util.Log;

import org.disrupted.rumble.util.RumblePreferences;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class EventLogger {

    public static final String TAG = "EventLogger";

    private static EventLogger logger;

    private EventLogger() {
    }

    public static EventLogger getInstance() {
        if (logger == null)
            logger = new EventLogger();
        return logger;
    }

    public void init() {
        if(RumblePreferences.isLogcatDebugEnabled(RumbleApplication.getContext())) {
            logger.start();
        } else {
            logger.stop();
        }
    }

    private void start() {
        if(!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this,10);
    }

    private void stop() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
    }
    
    public void onEvent(RumbleEvent event) {
        Log.d(TAG, "---> "+event.getClass().getSimpleName()+" : "+event.shortDescription());
    }

}
