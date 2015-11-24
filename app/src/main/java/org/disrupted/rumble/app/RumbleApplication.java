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

package org.disrupted.rumble.app;

import android.app.Application;
import android.content.Context;
import android.content.Intent;

import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.statistics.StatisticManager;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.database.CacheManager;
import org.disrupted.rumble.util.RumblePreferences;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class RumbleApplication extends Application{

    public static String BUILD_VERSION = "FOUCAULT";
    public static String BUILD_NUMBER = "1.0";

    private static RumbleApplication instance;
    private static EventLogger logger;

    public RumbleApplication() {
        instance = this;
    }

    public void debugging() {
        if(logger == null)
            logger = new EventLogger();

        if(RumblePreferences.isLogcatDebugEnabled(this))
            logger.start();
        else
            logger.stop();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        debugging();

        DatabaseFactory.getInstance(this);
        CacheManager.getInstance().start();
        StatisticManager.getInstance().start();

        if(DatabaseFactory.getContactDatabase(this).getLocalContact() != null) {
            Intent startIntent = new Intent(this, NetworkCoordinator.class);
            startIntent.setAction(NetworkCoordinator.ACTION_START_FOREGROUND);
            startService(startIntent);
        } else {
            if(!EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().register(this);
        }
    }

    public void onEvent(ContactInsertedEvent event) {
        if(event.contact.isLocal()) {
            Intent startIntent = new Intent(this, NetworkCoordinator.class);
            startIntent.setAction(NetworkCoordinator.ACTION_START_FOREGROUND);
            startService(startIntent);
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }

    public static Context getContext() {
        return instance;
    }

    public static RumbleApplication getApplication() {
        return instance;
    }
}
