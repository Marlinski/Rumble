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

package org.disrupted.rumble.network;


import android.util.Log;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Marlinski
 */
public class ThreadPoolCoordinator {

    private static final String TAG = "ThreadPoolCoordinator";

    public static final int PRIORITY_HIGH        =  5;
    public static final int PRIORITY_MIDDLE_HIGH =  10;
    public static final int PRIORITY_MIDDLE      =  15;
    public static final int PRIORITY_MIDDLE_LOW  =  20;
    public static final int PRIORITY_LOW         =  25;

    private static final Integer WORKER_THREAD_NB = 5;

    private static final Object lock             = new Object();
    private static ThreadPoolCoordinator instance;

    private PriorityBlockingQueue<PriorityNetworkThread> networkThreadsQueue;
    private LinkedList<PoolThread> pool;
    private static final Object lockThreadPool   = new Object();
    private static final Object lockThreadQueue  = new Object();

    public static ThreadPoolCoordinator getInstance() {
        synchronized (lock) {
            if (instance == null)
                instance = new ThreadPoolCoordinator(WORKER_THREAD_NB);

            return instance;
        }
    }

    private ThreadPoolCoordinator(int N) {
        networkThreadsQueue = new PriorityBlockingQueue<PriorityNetworkThread>();
        pool = new LinkedList<PoolThread>();
        for (int i = 0; i < N; i++) {
            PoolThread thread = new PoolThread("Thread "+i);
            thread.start();
            pool.add(thread);
        }
    }

    private boolean alreadyInThread(PriorityNetworkThread priorityNetworkThread) {
        synchronized (lockThreadPool) {
            Iterator<PoolThread> it = pool.iterator();
            while (it.hasNext()) {
                PoolThread element = it.next();
                if (element.getNetworkThread() != null) {
                    if (element.getNetworkThread().getNetworkThreadID()
                            .equals(priorityNetworkThread.getNetworkThread().getNetworkThreadID()))
                    return true;
                }
            }
        }
        return false;
    }

    public PriorityNetworkThread alreadyInQueue(PriorityNetworkThread priorityNetworkThread) {
        synchronized (lockThreadQueue) {
            Iterator<PriorityNetworkThread> it = networkThreadsQueue.iterator();
            while (it.hasNext()) {
                PriorityNetworkThread element = it.next();
                if (element.getNetworkThread().getNetworkThreadID()
                        .equals(priorityNetworkThread.getNetworkThread().getNetworkThreadID()))
                    return element;
            }
        }
        return null;
    }


    public int killThreadType(String type) {
        int nbKilled = 0;
        synchronized (lockThreadQueue) {
            Iterator<PriorityNetworkThread> it = networkThreadsQueue.iterator();
            while (it.hasNext()) {
                PriorityNetworkThread element = it.next();
                if (element.getNetworkThread().getType().equals(type)) {
                    it.remove();
                    nbKilled++;
                }
            }
        }
        synchronized (lockThreadPool) {
            Iterator<PoolThread> it = pool.iterator();
            while (it.hasNext()) {
                PoolThread element = it.next();
                if (element.getNetworkThread() != null) {
                    if (element.getNetworkThread().getType().equals(type)) {
                        element.killNetworkThread();
                        it.remove();
                        nbKilled++;
                    }
                }
            }
        }
        return  nbKilled;
    }

    public boolean addNetworkThread(NetworkThread networkThread, int priority) {
        PriorityNetworkThread con = new PriorityNetworkThread(networkThread, priority);
        if(alreadyInThread(con)) {
            Log.d(TAG, "[-] same thread already exists in pool");
            return false;
        }
        PriorityNetworkThread exists = alreadyInQueue(con);
        if(exists == null){
            Log.d(TAG, "[+] "+networkThread.getNetworkThreadID()+" added to ThreadQueue");
            networkThreadsQueue.add(con);
            return true;
        }
        if(exists.getPriority() < priority) {
            Log.d(TAG, "[-] same thread exists in queue with more priority");
            return false;
        }
        networkThreadsQueue.remove(exists);
        Log.d(TAG, "[+] "+networkThread.getNetworkThreadID()+" added to ThreadQueue");
        networkThreadsQueue.add(con);
        return true;
    }

    public boolean addNetworkThread(NetworkThread networkThread) {
        return addNetworkThread(networkThread, PRIORITY_MIDDLE);
    }

    private class PriorityNetworkThread implements Comparable{

        private NetworkThread networkThread;
        private int priority;

        PriorityNetworkThread(NetworkThread networkThread, int priority){
            this.networkThread = networkThread;
            this.priority = priority;
        }

        public NetworkThread getNetworkThread() {
            return networkThread;
        }

        public int getPriority() {
            return priority;
        }

        @Override
        public int compareTo(Object obj) {
            if (!(obj instanceof  Runnable))
                return 10;
            if (obj == this)
                return 0;

            if(this.priority < ((PriorityNetworkThread)obj).getPriority())
                return -1;
            if(this.priority == ((PriorityNetworkThread)obj).getPriority())
                return 0;
            return 1;
        }
    }

    private class PoolThread extends Thread {

        private static final String TAG = "PoolThread";

        private NetworkThread networkThread;
        private boolean isRunning;

        PoolThread(String name) {
            super(name);
            networkThread = null;
            isRunning = false;
        }

        @Override
        public void run() {
            Log.d(TAG, "[+] "+this.getName()+" Started");

            while (true) {
                try {
                    networkThread = null;
                    isRunning = false;
                    PriorityNetworkThread element;

                    element = networkThreadsQueue.take();

                    if(element == null) continue;
                    //Log.d(TAG, "[+] "+this.getName()+" consumes "+element.getNetworkThread().getNetworkThreadID());
                    this.networkThread = element.getNetworkThread();
                    isRunning = true;
                    networkThread.runNetworkThread();
                } catch (InterruptedException e) {
                    Log.d(TAG, this.getName()+" interrupted ", e);
                }
            }
        }

        public NetworkThread getNetworkThread(){
            return networkThread;
        }

        public void killNetworkThread() {
            if(isRunning)
                networkThread.killNetworkThread();
        }
    }
}