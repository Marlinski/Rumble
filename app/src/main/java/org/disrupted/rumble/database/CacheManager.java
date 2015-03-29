package org.disrupted.rumble.database;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.events.StatusReceivedEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;

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
            started = false;
        }
    }

    public void onEvent(StatusSentEvent event) {
        if(!started)
            return;

        Iterator<String> it = event.recipients.iterator();
        while(it.hasNext())
            event.status.addForwarder(it.next(), event.protocolID);
        event.status.addReplication(event.recipients.size());

        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status, null);
    }

    public void onEvent(StatusReceivedEvent event) {
        if(!started)
            return;

        StatusMessage exists = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(event.status.getUuid());
        if(exists == null) {
            event.status.addForwarder(event.sender, event.protocolID);
            event.status.addDuplicate(1);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(event.status, null);
        } else {
            exists.addForwarder(event.sender, event.protocolID);
            exists.addDuplicate(1);
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status, null);
        }
    }


}
