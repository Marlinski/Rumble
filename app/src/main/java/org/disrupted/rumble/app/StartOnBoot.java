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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.disrupted.rumble.database.CacheManager;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.statistics.StatisticManager;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.util.RumblePreferences;

/**
 * @author Marlinski
 */
public class StartOnBoot extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if(!RumblePreferences.startOnBoot(context))
                return;

            if(DatabaseFactory.getContactDatabase(context).getLocalContact() != null) {
                Intent startIntent = new Intent(context, NetworkCoordinator.class);
                startIntent.setAction(NetworkCoordinator.ACTION_START_FOREGROUND);
                context.startService(startIntent);
            }
        }
    }
}