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

package org.disrupted.rumble.message;

import android.database.Cursor;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.events.NewStatusEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;


import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.PriorityBlockingQueue;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class MessageQueue {

    private static final String TAG = "MessageProcessor";

    private static final Object lock           = new Object();
    private static MessageQueue instance;


    //todo maybe using trove instead ?
    private TreeSet<ScoredEntry> priorityStatus;
    private boolean started;
    private static Set<PriorityBlockingMessageQueue> messageListeners;

    public static MessageQueue getInstance() {
        synchronized (lock) {
            if (instance == null)
                instance = new MessageQueue();

            return instance;
        }
    }

    public class ScoredEntry {
        public long  statusID;
        public float score;
        public ScoredEntry(long  statusID, float score) {
            this.statusID = statusID;
            this.score = score;
        }

    }
    public static Comparator<ScoredEntry> scoredEntryComparator = new Comparator<ScoredEntry>() {
        @Override
        public int compare(ScoredEntry scoredEntry, ScoredEntry scoredEntry2) {
            if(scoredEntry.statusID == scoredEntry2.statusID)
                return 0;
            int diff = (int)(scoredEntry2.score - scoredEntry.score);
            return ((diff == 0) ? 1 : diff);
        }
    };
    private MessageQueue() {
        priorityStatus = new TreeSet<ScoredEntry>(scoredEntryComparator);
        messageListeners = new LinkedHashSet<PriorityBlockingMessageQueue>();
    }

    public void start() {
        if(!started) {
            Log.d(TAG, "[+] Starting Message Queue");
            getNewList();
            EventBus.getDefault().register(this);
            started = true;
        }
    }

    public void stop() {
        if(started) {
            Log.d(TAG, "[-] Stopping Message Queue");
            started = false;
            EventBus.getDefault().unregister(this);
            synchronized (lock) {
                for (ScoredEntry entry : priorityStatus) {
                    entry = null;
                }
                priorityStatus.clear();
            }
        }
    }

    private void getNewList() {
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getStatusesForScoring(scoringCallback);
    }
    DatabaseExecutor.ReadableQueryCallback scoringCallback = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(Cursor cursor) {
            if(cursor != null) {
                synchronized (lock) {
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(StatusDatabase.ID));
                        long hopcount = cursor.getLong(cursor.getColumnIndexOrThrow(StatusDatabase.HOP_COUNT));
                        long like = cursor.getLong(cursor.getColumnIndexOrThrow(StatusDatabase.LIKE));
                        long replication = cursor.getLong(cursor.getColumnIndexOrThrow(StatusDatabase.REPLICATION));
                        long toc = cursor.getLong(cursor.getColumnIndexOrThrow(StatusDatabase.TIME_OF_CREATION));
                        long ttl = cursor.getLong(cursor.getColumnIndexOrThrow(StatusDatabase.TTL));

                        float score = computeScore(hopcount, like, replication, toc, ttl);
                        priorityStatus.add(new ScoredEntry(id, score));
                    }
                }
                cursor.close();
            }
            Log.d(TAG, "[+] Message from database are ranked and ready to send");
        }
    };


    /*
     * ComputeScore is a CRITICAL Function. It defines the routing strategy. Here we took the
     * following Stance: The score is a weighted average computed as the following equation:
     *
     *      score = a * (HOPCOUNT+REPLICATION) + b * TOC + c * LIKE
     *
     * However the weight are not fixed. For instance, we value of like increase as the message
     * gets older and has been replicated, but it doesn't have any sense to take like into
     * consideration when the message is newly created.
     *
     * Thus, 'a' = 100% at the beginning and decrease as HOPCOUNT and REPLICATION increase
     * a is calculated as the following:
     *
     *                                 100
     *          a = min(100, ------------------------------
     *                        log(HOPCOUNT+REPLICATION+1)+1
     *
     *
     * Then, 'b' also decrease over time following almost the same formulae (taking by hour)
     *                                 100-a
     *          b = min(100-a, ------------------------------
     *                        log((currentTime - TOC)/3600+1)+1
     *
     * Finally 'c' takes the rest:
     *
     *          c = 100 - a - b
     */
    private static float computeScore(long hopcount, long like, long replication, long toc, long ttl){

        long old = System.currentTimeMillis() - toc;
        int a = Math.min(100, 100/((int)Math.log(hopcount+replication+1)+1));
        int b = Math.min(100 - a, (100 - a) / ((int) Math.log((old / 3600) + 1) + 1));
        int c = 100-a-b;

        return (a + b*(100/((int)Math.log((old/3600)+1)+1)) + c*(100*like/(1+like))/(a+b+c)) ;
    }

    public void onEvent(NewStatusEvent event) {
        StatusMessage message = event.status;
        long statusID = event.statusID;
        if(message == null)
            return;

        //todo WARNING it is going to be mis-sorted as this score is more recent than those in Queue
        float score = computeScore(
                message.getHopCount(),
                message.getLike(), message.getReplication(),
                message.getTimeOfCreation(),
                message.getTTL());

        synchronized (lock) {
            priorityStatus.add(new ScoredEntry(statusID, score));

            for (PriorityBlockingMessageQueue listener : messageListeners) {
                listener.insertMessage(event.status);
            }
        }
    }

    public void onEvent(StatusSentEvent event) {
        Iterator<String> it = event.recipients.iterator();
        while(it.hasNext())
            event.status.addForwarder(it.next(), event.protocolID);
        event.status.addReplication(event.recipients.size());
        float score = computeScore(
                event.status.getHopCount(),
                event.status.getLike(),
                event.status.getReplication(),
                event.status.getTimeOfCreation(),
                event.status.getTTL()
                );

        /*
         * the comparator return equal if the ID are equal. so we first remove the old entry
         * and then we insert the new one !
         * todo: WARNING it is going to be missorted as the others hold an old score
         *       solution: precompute a+c without b, compute total score when comparing
         */
        ScoredEntry entry = new ScoredEntry(event.status.getdbId(), score);
        synchronized (lock) {
            priorityStatus.remove(entry);
            priorityStatus.add(entry);

            for (PriorityBlockingMessageQueue listener : messageListeners) {
                listener.updateMessage(event.status);
            }
        }

        //todo update the database as well
    }




    /*
     * PriorityBlockingMessageQueue constructs and maintain an up-to-date a sorted
     * PriorityBlockingQueue of StatusMessage that is synchronized with the one in
     * MessageQueue.
     *
     * it implements the blocking take() operation which block until there is at least
     * one element in the queue. Its size is limited so as not to holds too many StatusMessage
     * in memory as the database grows. However, it automatically grows gets a new batch of
     * StatusMessage if needed.
     */
    public PriorityBlockingMessageQueue getMessageListener(int size) {
        return new PriorityBlockingMessageQueue(size);
    }

    public class PriorityBlockingMessageQueue {

        public Comparator<StatusMessage> statusComparator = new Comparator<StatusMessage>() {
            @Override
            public int compare(StatusMessage status1, StatusMessage status2) {
                if(status1.getdbId() == status2.getdbId())
                    return 0;

                float score1 = computeScore(
                        status1.getHopCount(),
                        status1.getLike(),
                        status1.getReplication(),
                        status1.getTimeOfCreation(),
                        status1.getTTL());
                float score2 = computeScore(
                        status2.getHopCount(),
                        status2.getLike(),
                        status2.getReplication(),
                        status2.getTimeOfCreation(),
                        status2.getTTL());
                int diff = (int)(score2 - score1);
                return ((diff == 0) ? 1 : diff);
            }
        };

        private PriorityBlockingQueue<StatusMessage> messageQueue;
        private long lastStatusID;
        private int size;
        private boolean dbEmpty;

        public PriorityBlockingMessageQueue(int size) {
            messageQueue = new PriorityBlockingQueue<StatusMessage>(size, statusComparator);
            lastStatusID = -1;
            this.size = size;
            dbEmpty = false;
            getNewBatch();
            synchronized (lock) {
                messageListeners.add(this);
            }
        }

        public StatusMessage take() throws InterruptedException{
            /*
             * dbEmpty is true whenever we have already forwarded (or discarded) every message
             * from our database. In that case messageQueue.take() will block until
             * MessageQueue.onEvent() calls this.insertNewMessage;
             *
             * if dbEmpty is false, we automatically increase our buffer by getting a new batch
             * of message.
             */
            if(!dbEmpty) {
                if (messageQueue.size() == 0)
                    getNewBatch();
            }
            StatusMessage entry = messageQueue.take();
            lastStatusID = entry.getdbId();
            return entry;
        }


        private int getNewBatch() {
            Log.d(TAG, "[+] getting new batch: "+lastStatusID);
            List<Integer> idList = new LinkedList<Integer>();
            synchronized (lock) {
                Iterator<ScoredEntry> it = priorityStatus.iterator();
                if (!it.hasNext()) {
                    dbEmpty = true;
                    return 0;
                }

                /*
                 * our new batch is filled from our lastStatusID to the maximum capacity of our
                 * queue, or if the entire database has been dumped already
                 */
                while (it.hasNext() && (idList.size() <= size)) {
                    ScoredEntry entry = it.next();
                    if ((lastStatusID < 0) || (lastStatusID > entry.statusID))
                        idList.add(Integer.valueOf((int)entry.statusID));
                }
                if(!it.hasNext())
                    dbEmpty = true;
            }
            /*
             * We now retrieve the status from the ScoredEntry list, if any.
             */
            if(idList.size() > 0) {
                Cursor cursor = DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).getBatchStatus(idList);
                if(cursor != null) {
                    for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                        StatusMessage message = StatusDatabase.cursorToStatus(cursor);
                        messageQueue.add(message);
                        Log.d(TAG, ":"+message.getdbId());
                    }
                    cursor.close();
                }
            }
            return messageQueue.size();
        }

        public void insertMessage(StatusMessage message) {
                messageQueue.add(message);
                Log.d(TAG, "[+] 1 messages added to queue");
        }


        public void updateMessage(StatusMessage message) {
                if(messageQueue.remove(message)) {
                    messageQueue.add(message);
                    Log.d(TAG, "[+] 1 message updated");
                }
        }

        public void clear() {
            messageQueue.clear();
        }
    }

}
