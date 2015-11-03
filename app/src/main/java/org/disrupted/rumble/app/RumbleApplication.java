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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.GroupDatabase;
import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.events.DatabaseEvent;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.database.CacheManager;
import org.disrupted.rumble.network.protocols.events.PushStatusReceived;
import org.disrupted.rumble.util.HashUtil;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class RumbleApplication extends Application{

    private static RumbleApplication instance;

    // name of the Rumble Image directory as it appears on Photo Album
    public static String RUMBLE_IMAGE_ALBUM_NAME = "Rumble";

    private static String RUMBLE_AUTHOR_NAME = "Marlinski (http://disruptedsystems.org)";

    public  static boolean LOG_EVENT = true;
    private static EventLogger logger;

    public RumbleApplication() {
        instance = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if(LOG_EVENT)
            logger = new EventLogger();

        DatabaseFactory.getInstance(this);
        CacheManager.getInstance().start();

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
            Contact marlinski = new Contact(RUMBLE_AUTHOR_NAME, "/Marlinski/=", false);
            Group publicGroup = new Group(Group.DEFAULT_PUBLIC_GROUP, HashUtil.computeGroupUid(Group.DEFAULT_PUBLIC_GROUP,false),null);

            long time = System.nanoTime();
            PushStatus message1 = new PushStatus(marlinski, publicGroup,getResources().getString(R.string.welcome_notice),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message1);
            time = System.nanoTime();
            PushStatus message2 = new PushStatus(marlinski, publicGroup,getResources().getString(R.string.swipe_left),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message2);
            time = System.nanoTime();
            PushStatus message3 = new PushStatus(marlinski, publicGroup,getResources().getString(R.string.swipe_right),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message3);
            PushStatus message4 = new PushStatus(marlinski, publicGroup,getResources().getString(R.string.swipe_down),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message4);


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
}
