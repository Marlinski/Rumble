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
import org.disrupted.rumble.database.objects.StatusMessage;
import org.disrupted.rumble.network.events.FileReceivedEvent;
import org.disrupted.rumble.network.events.StatusReceivedEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;
import org.disrupted.rumble.userinterface.events.UserCreateGroup;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
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
        Log.d(TAG, "[+] status sent: "+event.status.toString());
        StatusMessage status = new StatusMessage(event.status);
        Iterator<String> it = event.recipients.iterator();
        while(it.hasNext())
            status.addForwarder(it.next(), event.protocolID);
        status.addReplication(event.recipients.size());
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(status, null);
    }

    public void onEvent(StatusReceivedEvent event) {
        Log.d(TAG, "[+] received status: "+event.status.toString());
        StatusMessage exists = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.status.getUuid());
        if(exists == null) {
            StatusMessage status = new StatusMessage(event.status);
            status.addForwarder(event.sender, event.protocolID);
            status.addDuplicate(1);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(status, null);
            Log.d(TAG, "[+] status inserted: "+status.toString());
        } else {
            exists.addForwarder(event.sender, event.protocolID);
            exists.addDuplicate(1);
            if(event.status.getLike() > 0)
                exists.addLike();
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(exists, null);
            Log.d(TAG, "[+] status updated: "+exists.toString());
        }
    }
    public void onEvent(FileReceivedEvent event) {
        StatusMessage exists = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if((exists != null) && !exists.hasAttachedFile()) {
            exists.setFileName(event.filename);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(exists, null);
            Log.d(TAG, "[+] status updated: " + exists.getUuid());
            return;
        }
        try {
            File toDelete = new File(FileUtil.getWritableAlbumStorageDir(), event.filename);
            if (toDelete.exists() && toDelete.isFile())
                toDelete.delete();
        }catch(IOException ignore){
        }
    }

    public void onEvent(UserReadStatus event) {
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        }
    }
    public void onEvent(UserLikedStatus event) {
        Log.d(TAG, " [.] status "+event.uuid+" liked");
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        }
    }
    public void onEvent(UserSavedStatus event) {
        Log.d(TAG, " [.] status "+event.uuid+" saved");
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        if(message != null) {
            message.setUserRead(true);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        }
    }
    public void onEvent(UserDeleteStatus event) {
        Log.d(TAG, " [.] status "+event.uuid+" deleted");
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).deleteStatus(event.uuid, null);
    }

    public void onEvent(UserComposeStatus event) {
        if(event.status == null)
            return;
        Log.d(TAG, " [.] user composed status: "+event.status.toString());
        StatusMessage status = new StatusMessage(event.status);
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(status, null);
    }
    public void onEvent(UserCreateGroup event) {
        if(event.group == null)
            return;
        Log.d(TAG, " [.] user created group: "+event.group.getName());
        DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).insertGroup(event.group);
    }
    public void onEvent(UserJoinGroup event) {
        if(event.group == null)
            return;
        Log.d(TAG, " [.] user joined group: "+event.group.getName());
        DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).insertGroup(event.group);
    }
}
