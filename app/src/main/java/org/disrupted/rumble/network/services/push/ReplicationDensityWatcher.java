package org.disrupted.rumble.network.services.push;

import org.disrupted.rumble.network.protocols.events.PushStatusReceived;

import java.util.HashMap;
import java.util.Map;
import android.os.Handler;
import org.disrupted.rumble.util.Log;

import de.greenrobot.event.EventBus;

/**
 * The replication density metric keeps track of the number of copies received Cu for every message
 * u within a certain time window WS.
 *
 * When requested, it can compute the replication density metric of a given message u as
 * following:
 *              RDu(WS) = Cu(WS) / Nu(WS)
 *
 * @author Lucien Loiseau
 */
public class ReplicationDensityWatcher {

    private static final String TAG = "RDWatcher";

    private boolean started;
    private long windowSize;   //window size in seconds
    private Handler handler;
    Map<String, Integer> copiesReceived;
    int messageReceived;

    public ReplicationDensityWatcher(long windowSize) {
        started = false;
        handler = new Handler();
        this.windowSize = windowSize;
        this.messageReceived = 0;
        copiesReceived = new HashMap<String, Integer>();
    }

    public void start() {
        if(started)
            return;
        started = true;

        Log.d(TAG, "[+] RD Watcher Started");
        EventBus.getDefault().register(this);
    }

    public void stop() {
        if(!started)
            return;
        started = false;

        Log.d(TAG, "[-] RD Watcher Stopped");
        handler.removeCallbacksAndMessages(null);
        copiesReceived.clear();

        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
    }

    public float computeMetric(String uuid) {
        Integer nbOfCopies = copiesReceived.get(uuid);
        if(nbOfCopies == null)
            return 1;

        if(messageReceived == 0)
            return 1;

        return (1-(nbOfCopies / messageReceived));
    }

    public void onEvent(PushStatusReceived event) {
        final String uuid = event.status.getUuid();
        Integer nbOfCopies = copiesReceived.get(uuid);
        if(nbOfCopies == null)
            copiesReceived.put(uuid, 1);
        else
            copiesReceived.put(uuid, nbOfCopies++);
        messageReceived++;

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Integer nbOfCopies = copiesReceived.get(uuid);
                copiesReceived.put(uuid, nbOfCopies--);
                messageReceived--;
            }
        }, windowSize);
    }

}
