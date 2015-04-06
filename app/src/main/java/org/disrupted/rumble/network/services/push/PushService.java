package org.disrupted.rumble.network.services.push;

import android.database.Cursor;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.protocols.CommandExecutor;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class PushService {

    private static final String TAG = "PushService";

    private static final ReentrantLock lock = new ReentrantLock();
    private static final Condition serviceStarted = lock.newCondition();
    private static PushService instance;

    private static List<Long> statusIds;
    private static ReplicationDensityWatcher rdwatcher;
    private static final Random random = new Random();

    private PushService() {
        statusIds = new LinkedList<Long>();
        rdwatcher = new ReplicationDensityWatcher(1000*3600);
    }

    public static void startService() {
        lock.lock();
        try {
            Log.d(TAG, "[.] Starting PushService");
            if (instance == null) {
                instance = new PushService();
                rdwatcher.start();
                instance.initStatuses();
                EventBus.getDefault().register(instance);
            }
        } finally {
            lock.unlock();
        }
    }

    // todo: not sure who is responsible for shutting down the push service
    // maybe the UI ? Or should it be the NetworkCoordinator ?
    public static void stopService() {
        lock.lock();
        try {
            Log.d(TAG, "[-] Stopping PushService");
            if (instance != null) {
                if (EventBus.getDefault().isRegistered(instance))
                    EventBus.getDefault().unregister(instance);
                statusIds.clear();
                rdwatcher.stop();
                instance = null;
            }
        } finally {
            lock.unlock();
        }
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
                lock.lock();
                try {
                    do {
                        statusIds.add(answer.getLong(answer.getColumnIndexOrThrow(StatusDatabase.ID)));
                    } while (answer.moveToNext());
                    Log.d(TAG, "[+] PushService initiated");
                    serviceStarted.signal();
                } finally {
                    lock.unlock();
                }
            }
            answer.close();
        }
    };

    public void onEvent(StatusInsertedEvent event) {
        lock.lock();
        try {
            statusIds.add(event.status.getdbId());
        } finally {
            lock.unlock();
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
        //Log.d(TAG, "quality="+quality+" rd="+replicationDensity+" quality="+quality+" age="+age+" score="+score+" --- "+message.toString());

        return score;
    }

    // todo: not being dependant on age would make it so much easier ....
    public static class MessageDispatcher extends Thread {

        private static final String TAG = "MessageDispatcher";

        private CommandExecutor executor;
        private InterestVector interestVector;
        private ArrayList<Long> statuses;
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

        public MessageDispatcher(CommandExecutor executor, InterestVector interestVector, float threshold) {
            this.running = false;
            this.executor = executor;
            this.max = null;
            this.threshold = threshold;
            this.interestVector = interestVector;
            statuses = new ArrayList<Long>();
        }

        @Override
        public void run() {
            Log.d(TAG, "[.] MessageDispatcher started");
            running = true;
            try {
                init();
                Log.d(TAG, "[+] MessageDispatcher initiated");
                do {
                    // pause the process if the PushService is stopped
                    lock.lockInterruptibly();
                    try {
                        while (instance == null)
                            serviceStarted.await();
                    } catch(InterruptedException ie) {
                        throw ie;
                    } finally {
                        serviceStarted.signal(); // propagate signal
                        lock.unlock();
                    }

                    // pickup a message and send it to the CommandExecutor
                    if (executor != null) {
                        StatusMessage message = pickMessage();
                        Log.d(TAG, "message picked");
                        executor.execute(new SendStatusMessageCommand(message));
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
            this.start();
        }

        public void stopDispatcher() {
            this.interrupt();
            running = false;
            executor = null;
        }

        private void init() {
            fullyLock(); // locking CommandExecutor
            lock.lock(); // locking PushService
            try {
                for (Long s : statusIds) {
                    StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext())
                            .getStatus(s);
                    float score = computeScore(message, interestVector);

                    if (score <= threshold) {
                        message.discard();
                        continue;
                    }

                    add(message);
                }
                EventBus.getDefault().register(this);
            } finally {
                fullyUnlock();
                lock.unlock();
            }
        }

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
            final ReentrantLock putlock = this.putLock;
            putlock.lock();
            try {
                float score = computeScore(message, interestVector);

                if (score <= threshold) {
                    message.discard();
                    return false;
                }

                statuses.add(message.getdbId());

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

            Iterator<Long> it = statuses.iterator();
            while(it.hasNext()) {
                Long id = it.next();
                StatusMessage message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext())
                        .getStatus(id);
                float score = computeScore(max, interestVector);
                if(score <= threshold) {
                    message.discard();
                    statuses.remove(message.getdbId());
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

                    putlock.lock();
                    try {
                        updateMax();

                        // randomly pickup an element homogeneously
                        int index = random.nextInt(statuses.size());
                        long id = statuses.get(index);
                        message = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatus(id);

                        // get max probability Pmax
                        float maxScore = computeScore(max, interestVector);
                        // get element probability Pu
                        float score = computeScore(message, interestVector);

                        if (score <= threshold) {
                            // the message is not valid anymore, that should happen very rarely
                            statuses.remove(message.getdbId());
                            message.discard();
                            message = null;
                            continue;
                        }

                        int shallwepick = random.nextInt((int) (maxScore * 1000));
                        if (shallwepick <= (score * 1000)) {
                            // we keep this status with probability Pu/Pmax
                            statuses.remove(message.getdbId());
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

        public void onEvent(StatusInsertedEvent event) {
            add(event.status);
        }
    }
}
