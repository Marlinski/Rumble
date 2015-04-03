package org.disrupted.rumble.network.services.push;

import android.database.Cursor;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.events.StatusUpdatedEvent;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.services.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class PushService implements Service{

    private final Object lock = new Object();

    private Set<Long> statusIds;
    private ReplicationDensityWatcher rdwatcher;
    private static final Random random = new Random();

    public PushService() {
        rdwatcher = new ReplicationDensityWatcher(1000*3600);
    }

    @Override
    public void startService() {
        initStatuses();
        rdwatcher.start();
    }
    @Override
    public void stopService() {
        synchronized (lock) {
            statusIds.clear();
        }
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
                synchronized (lock) {
                    do {
                        statusIds.add(answer.getLong(answer.getColumnIndexOrThrow(StatusDatabase.ID)));
                    } while (answer.moveToNext());
                }
            }
            answer.close();

        }
    };

    private float computeScore(StatusMessage message, InterestVector interestVector) {
        //todo InterestVector for relevance
        float relevance = 0;
        float quality = (float)message.getLike()/(float)message.getDuplicate();
        float replicationDensity = rdwatcher.computeMetric(message.getUuid());
        float age = (message.getTTL() < 0) ? 1 : (1- (System.currentTimeMillis() - message.getTimeOfCreation())/message.getTTL());
        boolean distance = true;

        float a = 0;
        float b = (float)0.6;
        float c = (float)0.4;

        return (a*relevance + b*replicationDensity + c*quality)*age*(distance ? 1 : 0);
    }


    // todo: not being dependant on age would make it so much easier ....
    private class MessageDispatcher {

        private InterestVector interestVector;
        private ArrayList<Long> statuses;
        private float threshold;

        private final ReentrantLock putlock = new ReentrantLock(true);
        private final ReentrantLock takelock = new ReentrantLock(true);
        private final Condition notEmpty = takelock.newCondition();

        private StatusMessage max;

        private void fullyLock() {
            putlock.lock();
            takelock.lock();
        }

        private void fullyUnlock() {
            putlock.unlock();
            takelock.unlock();
        }

        public MessageDispatcher(InterestVector interestVector) {
            this.max = null;
            float maxScore = 0;
            this.threshold = 0;
            this.interestVector = interestVector;
            statuses = new ArrayList<Long>();
        }

        public void start() {
            fullyLock();
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
            }
        }

        public void stop() {
            fullyLock();
            try {
                statuses.clear();
                if(EventBus.getDefault().isRegistered(this))
                    EventBus.getDefault().unregister(this);
            } finally {
                fullyUnlock();
            }
        }

        private boolean add(StatusMessage message) {
            final ReentrantLock putlock = this.putlock;
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

                notEmpty.signal();
                return true;
            } finally {
                putlock.unlock();
            }
        }

        // todo: the complexity is DAMN TOO HIGH !!
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
        public StatusMessage pickMessage() throws InterruptedException{
            final ReentrantLock takelock = this.takelock;
            final ReentrantLock putlock = this.takelock;
            StatusMessage message;
            boolean pickup = false;
            takelock.lockInterruptibly();
            try {
                do {
                    try {
                        while (statuses.size() == 0)
                            notEmpty.await();
                    } catch (InterruptedException ie) {
                        notEmpty.signal(); // propagate to non-interrupted thread
                        throw ie;
                    }
                    putlock.lock();
                    try {
                        updateMax();

                        // get random element
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
                        } else if (random.nextInt((int) (maxScore * 1000)) <= (score * 1000)) {
                            // we keep it with probability Pu/Pmax
                            statuses.remove(message.getdbId());
                            pickup = true;
                        } else {
                            // else we try again
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
            if(statuses == null)
                return;
            add(event.status);

        }
    }
}
