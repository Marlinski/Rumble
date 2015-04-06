package org.disrupted.rumble.database;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.events.StatusReceivedEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;
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

    public void onEvent(UserReadStatus event) {
        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.uuid);
        message.setUserRead(true);
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(message, null);
        message.discard();
        message = null;
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
}
