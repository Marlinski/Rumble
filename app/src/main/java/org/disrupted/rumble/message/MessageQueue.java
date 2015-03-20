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
 * MessageQueue is a singleton class which maintain a sorted queue of every statuses from
 * the database.
 *
 *   When a new neighbour is within reach, a copy of this queue is pass to the NeighbourManager
 * (PriorityBlockingMessageQueue) so that status can be shared (from more priority to less).
 *
 *   Whever a new status is received by one of the neighbour, it is added to
 * the sorted queue as well as to every copy of the queue.
 *
 * @author Marlinski
 */
public class MessageQueue {

    private static final String TAG = "MessageQueue";

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

    /*
     * The whole status database is hold into priorityStatus as a sorted set. To avoid consuming too
     * much memory, the item only hold the Table ID, and its "score" (see computeScore below).
     * However, as the "oldness" of a status vary every millisecond, we compute a pre-score at
     * boot time and every comparison adds to this score the uptodate oldness.
     */
    public class ScoredEntry {
        public long  statusID;
        public Prescore prescore;
        public long  toc;
        public ScoredEntry(long  statusID, Prescore prescore, long toc) {
            this.statusID = statusID;
            this.prescore = prescore;
            this.toc = toc;
        }
    }
    public static Comparator<ScoredEntry> scoredEntryComparator = new Comparator<ScoredEntry>() {
        @Override
        public int compare(ScoredEntry entry1, ScoredEntry entry2) {
            if(entry1.statusID == entry2.statusID)
                return 0;
            int score1 =  (computeScore(entry1.prescore, entry1.toc));
            int score2 =  (computeScore(entry2.prescore, entry2.toc));
            return ((score1 > score2) ? -1 : 1);
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
            if(EventBus.getDefault().isRegistered(this))
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

                        Prescore prescore = computePreScore(hopcount, like, replication, ttl);
                        priorityStatus.add(new ScoredEntry(id, prescore, toc));
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
    private static class Prescore {
        public int a;
        public float C;
        public Prescore(int a, float C) {
            this.a = a;
            this.C = C;
        }
    }
    private static Prescore computePreScore(long hopcount, long like, long replication, long ttl){
        int a = Math.min(100, 100/((int)Math.log(hopcount+replication+1)+1));
        float C = (100*like/(1+like));
        return new Prescore(a,C) ;
    }
    private static int computeScore(Prescore prescore, long toc) {
        long old = System.currentTimeMillis() - toc;
        int a = prescore.a;
        int b = Math.min(100 - a, (100 - a) / ((int) Math.log((old / 3600) + 1) + 1));
        int c = 100-a-b;
        return (int)(a + b*(100/((int)Math.log((old/3600)+1)+1)) + c*prescore.C);
    }

    public void onEvent(NewStatusEvent event) {
        StatusMessage message = event.status;
        long statusID = message.getdbId();
        if(message == null)
            return;

        Prescore prescore = computePreScore(
                message.getHopCount(),
                message.getLike(),
                message.getReplication(),
                message.getTTL());

        synchronized (lock) {
            priorityStatus.add(new ScoredEntry(statusID, prescore, message.getTimeOfCreation()));

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

        /*
         * we update the database and update all the messagequeue listeners
         */
        DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status, null);

        Prescore prescore = computePreScore(
                event.status.getHopCount(),
                event.status.getLike(),
                event.status.getReplication(),
                event.status.getTTL()
                );

        ScoredEntry entry = new ScoredEntry(event.status.getdbId(), prescore, event.status.getTimeOfCreation());
        synchronized (lock) {
            priorityStatus.remove(entry);
            priorityStatus.add(entry);

            for (PriorityBlockingMessageQueue listener : messageListeners) {
                listener.updateMessage(event.status);
            }
        }

    }


    /*
     * PriorityBlockingMessageQueue constructs and maintain an up-to-date a sorted
     * PriorityBlockingQueue of StatusMessage that is synchronized with the one in
     * MessageQueue.
     *
     * it implements the blocking take() operation which block until there is at least
     * one element in the queue. Its size is limited so as not to holds too many StatusMessage
     * in memory as the database grows. However, it automatically grows and gets the next batch of
     * StatusMessage if needed.
     */
    public PriorityBlockingMessageQueue getMessageListener(int size) {
        return new PriorityBlockingMessageQueue(size);
    }

    public class PriorityBlockingMessageQueue {

        private class StatusMessageComparator implements Comparator<StatusMessage> {
            @Override
            public int compare(StatusMessage entry1, StatusMessage entry2) {
                if(entry1.getdbId() == entry2.getdbId())
                    return 0;
                if(entry1.getUuid() == entry2.getUuid())
                    return 0;

                Prescore prescore1 = computePreScore(
                        entry1.getHopCount(),
                        entry1.getLike(),
                        entry1.getReplication(),
                        entry1.getTTL());
                int score1 = computeScore(prescore1, entry1.getTimeOfCreation());
                Prescore prescore2 = computePreScore(
                        entry2.getHopCount(),
                        entry2.getLike(),
                        entry2.getReplication(),
                        entry2.getTTL());
                int score2 = computeScore(prescore2, entry2.getTimeOfCreation());

                return (score1 > score2) ? -1 :
                       (score2 > score1) ?  1 :
                       (entry1.getTimeOfCreation() > entry2.getTimeOfCreation()) ? 1 : -1;
            }
        }

        private PriorityBlockingQueue<StatusMessage> messageQueue;
        private long lastStatusIDTaken;
        private int size;
        private boolean dbEmpty;

        public PriorityBlockingMessageQueue(int size) {
            messageQueue = new PriorityBlockingQueue<StatusMessage>(size, new StatusMessageComparator());
            lastStatusIDTaken = -1;
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
             * MessageQueue.onEvent() calls insertNewMessage (ie: a new status has been received)
             *
             * if dbEmpty is false, we automatically increase our buffer by getting a new batch
             * of message.
             */
            if(!dbEmpty) {
                if (messageQueue.size() == 0)
                    getNewBatch();
            }

            StatusMessage entry = messageQueue.take();
            lastStatusIDTaken = entry.getdbId();
            return entry;
        }


        private int getNewBatch() {
            //Log.d(TAG, "[+] getting new batch: "+ lastStatusIDTaken);
            List<Integer> idList = new LinkedList<Integer>();
            synchronized (lock) {
                Iterator<ScoredEntry> it = priorityStatus.iterator();
                if (!it.hasNext()) {
                    dbEmpty = true;
                    return 0;
                }

                /*
                 * our new batch is filled from our lastStatusIDTaken to the maximum capacity of our
                 * queue, or if the entire database has been dumped already
                 */
                while (it.hasNext() && (idList.size() <= size)) {
                    ScoredEntry entry = it.next();
                    if ((lastStatusIDTaken < 0) || (lastStatusIDTaken > entry.statusID))
                        idList.add(Integer.valueOf((int) entry.statusID));
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
                        messageQueue.offer(message);
                    }
                    cursor.close();
                }
            }
            return messageQueue.size();
        }

        public void insertMessage(StatusMessage message) {
            if(messageQueue.size() < size) {
                messageQueue.add(message);
                return;
            }
        }


        public void updateMessage(StatusMessage message) {
                if(messageQueue.remove(message)) {
                    messageQueue.add(message);
                }
        }

        public void clear() {
            synchronized (lock) {
                messageListeners.remove(this);
            }
            messageQueue.clear();
        }
    }

}
