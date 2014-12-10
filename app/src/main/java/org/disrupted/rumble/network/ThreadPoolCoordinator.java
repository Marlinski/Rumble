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

import org.disrupted.rumble.network.linklayer.Connection;

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

    private PriorityBlockingQueue<PriorityConnection> connectionQueue;
    private LinkedList<ConnectionThread> connectionThreads;
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
        connectionQueue   = new PriorityBlockingQueue<PriorityConnection>();
        connectionThreads = new LinkedList<ConnectionThread>();
        for (int i = 0; i < N; i++) {
            ConnectionThread thread = new ConnectionThread("Thread "+i);
            thread.start();
            connectionThreads.add(thread);
        }
    }

    private boolean alreadyInThread(PriorityConnection connection) {
        synchronized (lockThreadPool) {
            Iterator<ConnectionThread> it = connectionThreads.iterator();
            while (it.hasNext()) {
                ConnectionThread element = it.next();
                if (element.getConnection() != null) {
                    if (element.getConnection().getConnectionID().equals(connection.getConnection().getConnectionID()))
                    return true;
                }
            }
        }
        return false;
    }

    public PriorityConnection alreadyInQueue(PriorityConnection connection) {
        synchronized (lockThreadQueue) {
            Iterator<PriorityConnection> it = connectionQueue.iterator();
            while (it.hasNext()) {
                PriorityConnection element = it.next();
                if (element.getConnection().getConnectionID().equals(connection.getConnection().getConnectionID()))
                    return element;
            }
        }
        return null;
    }


    public int killThreadType(String type) {
        int nbKilled = 0;
        synchronized (lockThreadQueue) {
            Iterator<PriorityConnection> it = connectionQueue.iterator();
            while (it.hasNext()) {
                PriorityConnection element = it.next();
                if (element.getConnection().getType().equals(type)) {
                    it.remove();
                    nbKilled++;
                }
            }
        }
        synchronized (lockThreadPool) {
            Iterator<ConnectionThread> it = connectionThreads.iterator();
            while (it.hasNext()) {
                ConnectionThread element = it.next();
                if (element.getConnection() != null) {
                    if (element.getConnection().getType().equals(type)) {
                        element.killConnection();
                        it.remove();
                        nbKilled++;
                    }
                }
            }
        }
        return  nbKilled;
    }

    public boolean addConnection(Connection connection) {
        PriorityConnection con = new PriorityConnection(connection, PRIORITY_MIDDLE);
        if(alreadyInThread(con)){
            Log.d(TAG, "[-] connection already exists");
            return false;
        }
        if(alreadyInQueue(con) == null) {
            Log.d(TAG, "[+] add to ThreadQueue: "+connection.getConnectionID());
            connectionQueue.add(con);
            return true;

        } else {
            Log.d(TAG, "[-] connection already exists");
            return false;
        }
    }

    public boolean addConnection(Connection connection, int priority) {
        PriorityConnection con = new PriorityConnection(connection, priority);
        if(alreadyInThread(con)) {
            Log.d(TAG, "[-] connection already exists in thread");
            return false;
        }
        PriorityConnection exists = alreadyInQueue(con);
        if(exists == null){
            Log.d(TAG, "[+] connection "+connection.getConnectionID()+" added to ThreadQueue");
            connectionQueue.add(con);
            return true;
        }
        if(exists.getPriority() < priority) {
            Log.d(TAG, "[-] connection already exists with more priority");
            return false;
        }
        Log.d(TAG, "[-] connection already exists but has less priority");
        connectionQueue.remove(exists);
        Log.d(TAG, "[+] connection "+connection.getConnectionID()+" added to ThreadQueue");
        connectionQueue.add(con);
        return true;
    }

    private class PriorityConnection implements Comparable{

        private Connection connection;
        private int priority;


        PriorityConnection(Connection connection, int priority){
            this.connection = connection;
            this.priority = priority;
        }

        public Connection getConnection() {
            return connection;
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

            if(this.priority < ((PriorityConnection)obj).getPriority())
                return -1;
            if(this.priority == ((PriorityConnection)obj).getPriority())
                return 0;
            return 1;
        }
    }

    private class ConnectionThread extends Thread {

        private static final String TAG = "ConnectionThread";

        private Connection connection;
        private boolean isRunning;

        ConnectionThread(String name) {
            super(name);
            connection = null;
            isRunning = false;
        }

        @Override
        public void run() {
            Log.d(TAG, "[+] "+this.getName()+" Started");

            while (true) {
                try {
                    connection = null;
                    isRunning = false;
                    PriorityConnection element;

                    element = connectionQueue.take();

                    if(element == null) continue;
                    Log.d(TAG, "[+] "+this.getName()+" consumes "+element.getConnection().getConnectionID());
                    this.connection = element.getConnection();
                    isRunning = true;
                    connection.run();
                } catch (InterruptedException e) {
                    Log.d(TAG, this.getName()+" interrupted ", e);
                }
            }
        }

        public Connection getConnection(){
            return connection;
        }

        public void killConnection() {
            if(isRunning)
                connection.kill();
        }

        public void killThread() {
            killConnection();
            Thread.currentThread().interrupt();
        }
    }
}