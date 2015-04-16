package org.disrupted.rumble.network.services.push;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.GroupDatabase;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.objects.StatusMessage;
import org.disrupted.rumble.network.events.NeighbourConnected;
import org.disrupted.rumble.network.events.NeighbourDisconnected;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.services.exceptions.ServiceNotStarted;
import org.disrupted.rumble.network.services.exceptions.WorkerAlreadyBinded;
import org.disrupted.rumble.network.services.exceptions.WorkerNotBinded;
import org.disrupted.rumble.util.HashUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class PushService {

    private static final String TAG = "PushService";

    private static final Object lock = new Object();
    private static PushService instance;

    private static ReplicationDensityWatcher rdwatcher;
    private static final Random random = new Random();

    private static Map<String, MessageDispatcher> workerIdentifierTodispatcher;

    private PushService() {
        rdwatcher = new ReplicationDensityWatcher(1000*3600);
    }

    public static void startService() {
        if(instance != null)
            return;

        synchronized (lock) {
            Log.d(TAG, "[.] Starting PushService");
            if (instance == null) {
                instance = new PushService();
                rdwatcher.start();
                workerIdentifierTodispatcher = new HashMap<String, MessageDispatcher>();
                EventBus.getDefault().register(instance);
            }
        }
    }

    public static void stopService() {
        if(instance == null)
                return;
        synchronized (lock) {
            Log.d(TAG, "[-] Stopping PushService");
            if(EventBus.getDefault().isRegistered(instance))
                EventBus.getDefault().unregister(instance);

            for(Map.Entry<String, MessageDispatcher> entry : instance.workerIdentifierTodispatcher.entrySet()) {
                MessageDispatcher dispatcher = entry.getValue();
                dispatcher.interrupt();
            }
            instance.workerIdentifierTodispatcher.clear();
            rdwatcher.stop();
            instance = null;
        }
    }

    // todo: register protocol to service
    public void onEvent(NeighbourConnected neighbour) {
        if(instance != null) {
            if(!neighbour.worker.getProtocolIdentifier().equals(RumbleProtocol.protocolID))
                return;
            synchronized (lock) {
                MessageDispatcher dispatcher = instance.workerIdentifierTodispatcher.get(neighbour.worker.getWorkerIdentifier());
                if (dispatcher != null) {
                    Log.e(TAG, "worker already binded ?!");
                    return;
                }
                dispatcher = new MessageDispatcher(neighbour.worker);
                instance.workerIdentifierTodispatcher.put(neighbour.worker.getWorkerIdentifier(), dispatcher);
                dispatcher.startDispatcher();
            }
        }
    }

    public void onEvent(NeighbourDisconnected neighbour) {
        if(instance != null) {
            if(!neighbour.worker.getProtocolIdentifier().equals(RumbleProtocol.protocolID))
                return;
            synchronized (lock) {
                MessageDispatcher dispatcher = instance.workerIdentifierTodispatcher.get(neighbour.worker.getWorkerIdentifier());
                if (dispatcher == null)
                    return;
                dispatcher.stopDispatcher();
                instance.workerIdentifierTodispatcher.remove(neighbour.worker.getWorkerIdentifier());
            }
        }
    }

    private static float computeScore(StatusMessage message, InterestVector interestVector) {
        //todo InterestVector for relevance
        float relevance = 0;
        float replicationDensity = rdwatcher.computeMetric(message.getUuid());
        float quality =  (message.getDuplicate() == 0) ? 0 : (float)message.getLike()/(float)message.getDuplicate();
        float age = (message.getTTL() <= 0) ? 1 : (1- (System.currentTimeMillis() - message.getTimeOfCreation())/message.getTTL());
        boolean distance = true;

        float a = 0;
        float b = (float)0.6;
        float c = (float)0.4;

        float score = (a*relevance + b*replicationDensity + c*quality)*age*(distance ? 1 : 0);

        return score;
    }

    // todo: not being dependant on age would make it so much easier ....
    private static class MessageDispatcher extends Thread {

        private static final String TAG = "MessageDispatcher";

        private ProtocolWorker worker;
        private InterestVector interestVector;
        private ArrayList<Integer> statuses;
        private float threshold;

        // locks for managing the ArrayList
        private final ReentrantLock putLock = new ReentrantLock(true);
        private final ReentrantLock takeLock = new ReentrantLock(true);
        private final Condition notEmpty = takeLock.newCondition();

        private boolean running;

        private StatusMessage max;

        private void fullyLock() {
            putLock.lock();
            takeLock.lock();
        }
        private void fullyUnlock() {
            putLock.unlock();
            takeLock.unlock();
        }
        private void signalNotEmpty() {
            final ReentrantLock takeLock = this.takeLock;
            takeLock.lock();
            try {
                notEmpty.signal();
            } finally {
                takeLock.unlock();
            }
        }

        public MessageDispatcher(ProtocolWorker worker) {
            this.running = false;
            this.worker = worker;
            this.max = null;
            this.threshold = 0;
            this.interestVector = null;
            statuses = new ArrayList<Integer>();
        }

        @Override
        public void run() {
            try {
                Log.d(TAG, "[+] MessageDispatcher initiated");
                do {
                    // pickup a message and send it to the CommandExecutor
                    if (worker != null) {
                        StatusMessage message = pickMessage();
                        Log.d(TAG, "message picked");
                        worker.execute(new SendStatusMessageCommand(message));
                        message.discard();
                        //todo just for the sake of debugging
                        sleep(1000, 0);
                    }

                } while (running);

            } catch (InterruptedException ie) {
            } finally {
                clear();
                Log.d(TAG, "[-] MessageDispatcher stopped");
            }
        }

        public void startDispatcher() {
            running = true;
            initStatuses();
        }

        public void stopDispatcher() {
            running = false;
            this.interrupt();
            worker = null;
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }

        private void initStatuses() {
            StatusDatabase.StatusQueryOption options = new StatusDatabase.StatusQueryOption();
            options.filterFlags |= StatusDatabase.StatusQueryOption.FILTER_GROUP;
            options.groupList = new ArrayList<String>();
            options.groupList.add(GroupDatabase.DEFAULT_PUBLIC_GROUP);
            options.filterFlags |= StatusDatabase.StatusQueryOption.FILTER_NEVER_SEND;
            options.peerName = HashUtil.computeForwarderHash(
                    worker.getLinkLayerConnection().getRemoteLinkLayerAddress(),
                    worker.getProtocolIdentifier());
            options.query_result = StatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_IDS;
            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatuses(options, onStatusLoaded);
        }
        DatabaseExecutor.ReadableQueryCallback onStatusLoaded = new DatabaseExecutor.ReadableQueryCallback() {
            @Override
            public void onReadableQueryFinished(Object result) {
                if (result != null) {
                    Log.d(TAG, "[+] MessageDispatcher initiated");
                    final ArrayList<Integer> answer = (ArrayList<Integer>)result;
                    for (Integer s : answer) {
                        StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext())
                                .getStatus(s);
                        if(message != null) {
                            add(message);
                            message.discard();
                        }
                    }
                    EventBus.getDefault().register(MessageDispatcher.this);
                    start();
                }
            }
        };

        private void clear() {
            fullyLock();
            try {
                if(EventBus.getDefault().isRegistered(this))
                    EventBus.getDefault().unregister(this);
                statuses.clear();
            } finally {
                fullyUnlock();
            }
        }

        private boolean add(StatusMessage message){
            if(message.isForwarder(
                    worker.getLinkLayerConnection().getRemoteLinkLayerAddress(),
                    worker.getProtocolIdentifier()))
                return false;

            final ReentrantLock putlock = this.putLock;
            try {
                putlock.lock();

                float score = computeScore(message, interestVector);

                if (score <= threshold) {
                    message.discard();
                    return false;
                }
                statuses.add((int)message.getdbId());

                if (max == null) {
                    max = message;
                } else {
                    float maxScore = computeScore(max, interestVector);
                    if (score > maxScore) {
                        max.discard();
                        max = message;
                    } else
                        message.discard();
                }

                signalNotEmpty();
                return true;
            } finally {
                putlock.unlock();
            }
        }

        // todo: iterating over the entire array, the complexity is DAMN TOO HIGH !!
        private void updateMax() {
            float maxScore = 0;
            if(max != null) {
                maxScore = computeScore(max, interestVector);
                if(maxScore > threshold)
                    return;
            }

            ArrayList<Integer> toDelete = new ArrayList<Integer>();
            Iterator<Integer> it = statuses.iterator();
            while(it.hasNext()) {
                Integer id = it.next();
                StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext())
                        .getStatus(id);
                float score = computeScore(max, interestVector);
                if(score <= threshold) {
                    message.discard();
                    toDelete.add((int)message.getdbId());
                    continue;
                }

                if(max == null) {
                    max = message;
                    maxScore = score;
                    continue;
                }

                if(score > maxScore) {
                    max.discard();
                    max = message;
                    maxScore = score;
                } else
                    message.discard();
            }

            for(Integer i : toDelete) {
                statuses.remove(new Integer(i));
            }

        }

        /*
         *  See the paper:
         *  "Roulette-wheel selection via stochastic acceptance"
         *  By Adam Lipowski, Dorota Lipowska
         */
        private StatusMessage pickMessage() throws InterruptedException {
            final ReentrantLock takelock = this.takeLock;
            final ReentrantLock putlock = this.takeLock;
            StatusMessage message;
            boolean pickup = false;
            takelock.lockInterruptibly();
            try {
                do {
                    while (statuses.size() == 0)
                        notEmpty.await();

                    for(Integer id:statuses) {
                        Log.d(TAG, ","+id);
                    }
                    putlock.lock();
                    try {
                        updateMax();

                        // randomly pickup an element homogeneously
                        int index = random.nextInt(statuses.size());
                        long id = statuses.get(index);
                        message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(id);
                        if(message == null) {
                            //Log.d(TAG, "cannot retrieve statusId: "+id);
                            statuses.remove(new Integer((int)id));
                            continue;
                        }

                        // get max probability Pmax
                        float maxScore = computeScore(max, interestVector);
                        // get element probability Pu
                        float score = computeScore(message, interestVector);

                        if (score <= threshold) {
                            //Log.d(TAG, "score too low: "+score);
                            statuses.remove(new Integer((int)id));
                            message.discard();
                            message = null;
                            continue;
                        }

                        int shallwepick = random.nextInt((int) (maxScore * 1000));
                        if (shallwepick <= (score * 1000)) {
                            //Log.d(TAG, "we picked up: "+id);
                            // we keep this status with probability Pu/Pmax
                            statuses.remove(new Integer((int)message.getdbId()));
                            pickup = true;
                        } else {
                            // else we pick another one
                            message.discard();
                        }
                    } finally {
                        putlock.unlock();
                    }
                } while(!pickup);
            } finally {
                takelock.unlock();
            }
            return message;
        }

        public void onEvent(StatusDeletedEvent event) {
            fullyLock();
            try {
                statuses.remove(Integer.valueOf((int) event.dbid));
            } finally {
                fullyUnlock();
            }
        }

        public void onEvent(StatusInsertedEvent event) {
            StatusMessage message = new StatusMessage(event.status);
            add(message);
            message.discard();
        }
    }
}
