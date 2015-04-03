package org.disrupted.rumble.network.services.push;

import android.database.Cursor;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.protocols.CommandExecutor;
import org.disrupted.rumble.network.services.Service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Marlinski
 */
public class PushService implements Service{

    private Set<Long> statusIds;
    private Map<String, MessageDispatcher> dispatchers;
    private ReplicationDensityWatcher rdwatcher;

    public PushService() {
        this.dispatchers = new HashMap<String, MessageDispatcher>();
        rdwatcher = new ReplicationDensityWatcher(1000*3600);
    }

    @Override
    public void startService() {
        initStatuses();
        rdwatcher.start();
    }
    @Override
    public void stopService() {
        statusIds.clear();
        Iterator<Map.Entry<String, MessageDispatcher>> it = dispatchers.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String, MessageDispatcher> entry = it.next();
            entry.getValue().interrupt();
            it.remove();
        }
        dispatchers.clear();
        rdwatcher.stop();
    }

    private void initStatuses() {
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext())
                .getStatusesId(onStatusLoaded);
    }
    DatabaseExecutor.ReadableQueryCallback onStatusLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Cursor cursor) {
            final Cursor answer = cursor;
            if (answer.moveToFirst()) {
                do {
                    statusIds.add(answer.getLong(answer.getColumnIndexOrThrow(StatusDatabase.ID)));
                } while (answer.moveToNext());
            }
            answer.close();

        }
    };

    //todo InterestVector for relevance
    public float computeScore(StatusMessage message) {
        float relevance = 0;
        float quality = (float)message.getLike()/(float)message.getDuplicate();
        float replicationDensity = rdwatcher.computeMetric(message.getUuid());
        float age = (1- (System.currentTimeMillis() - message.getTimeOfCreation())/message.getTTL());
        boolean distance = true;

        float a = 0;
        float b = (float)0.6;
        float c = (float)0.4;

        return (a*relevance + b*replicationDensity + c*quality)*age*(distance ? 1 : 0);
    }

    private class MessageDispatcher extends Thread {

        private CommandExecutor protocol;
        private InterestVector interestVector;
        private Set<Long> statuses;

        MessageDispatcher(CommandExecutor protocol) {
            this.protocol = protocol;
            this.interestVector = null;
        }

        @Override
        public void run() {
            super.run();
            StatusMessage status = pickMessage();

        }

        public StatusMessage pickMessage() {
            return null;
        }
    }
}
