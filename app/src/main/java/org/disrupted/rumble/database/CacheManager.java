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

package org.disrupted.rumble.database;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.events.FileReceivedEvent;
import org.disrupted.rumble.network.events.StatusReceivedEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;

import java.util.Iterator;


import de.greenrobot.event.EventBus;

/**
 * The CacheManager takes care ongf updati the database accordingly to the catched event
 *
 * @author Marlinski
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    private static final Object globalQueuelock = new Object();
    private static CacheManager instance;

    private boolean started;

    public static CacheManager getInstance() {
        synchronized (globalQueuelock) {
            if (instance == null)
                instance = new CacheManager();

            return instance;
        }
    }

    public void start() {
        if(!started) {
            Log.d(TAG, "[+] Starting Cache Manager");
            started = true;
            EventBus.getDefault().register(this);
        }
    }

    public void stop() {
        if(started) {
            Log.d(TAG, "[-] Stopping Cache Manager");
            started = false;
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }

    public void onEvent(StatusSentEvent event) {
        Iterator<String> it = event.recipients.iterator();
        while(it.hasNext())
            event.status.addForwarder(it.next(), event.protocolID);
        event.status.addReplication(event.recipients.size());

        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status, null);
    }

    public void onEvent(StatusReceivedEvent event) {
        StatusMessage exists = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.status.getUuid());
        if(exists == null) {
            event.status.addForwarder(event.sender, event.protocolID);
            event.status.addDuplicate(1);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(event.status, null);
            Log.d(TAG, "[+] status inserted: "+event.status.toString());
        } else {
            exists.addForwarder(event.sender, event.protocolID);
            exists.addDuplicate(1);
            if(event.status.getLike() > 0)
                exists.addLike();
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(exists, null);
            Log.d(TAG, "[+] status updated: "+event.status.toString());
        }
    }
    public void onEvent(FileReceivedEvent event) {
        StatusMessage exists = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(exists == null) {
            Log.d(TAG, "[-] received file "+event.filename+" attached to an unknown status: "+event.uuid);
            return;
        }
        exists.setFileName(event.filename);
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(exists, null);
        Log.d(TAG, "[+] status updated: "+new String(exists.getUuid()));
    }

    public void onEvent(UserReadStatus event) {
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
            message.discard();
            message = null;
        }
    }
    public void onEvent(UserLikedStatus event) {
        Log.d(TAG, " [.] status "+event.uuid+" liked");
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message == null)
            return;
        message.setUserRead(true);
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        message.discard();
        message = null;
    }
    public void onEvent(UserSavedStatus event) {
        Log.d(TAG, " [.] status "+event.uuid+" saved");
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        message.setUserRead(true);
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        message.discard();
        message = null;
    }
    public void onEvent(UserDeleteStatus event) {
        Log.d(TAG, " [.] status "+event.uuid+" deleted");
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).deleteStatus(event.uuid, null);
    }

    public void onEvent(UserComposeStatus event) {
        if(event.status == null)
            return;
        Log.d(TAG, " [.] user composed status: "+event.status.toString());
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(event.status, null);
    }
}
