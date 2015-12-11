/*
 * Copyright (C) 2014 Lucien Loiseau
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


import org.disrupted.rumble.util.Log;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * @author Lucien Loiseau
 */
public class WorkerPool {

    private static final String TAG = "WorkerPool";

    public static final int PRIORITY_HIGH        =  5;
    public static final int PRIORITY_MIDDLE_HIGH =  10;
    public static final int PRIORITY_MIDDLE      =  15;
    public static final int PRIORITY_MIDDLE_LOW  =  20;
    public static final int PRIORITY_LOW         =  25;

    private PriorityBlockingQueue<QueueElement> queue;
    private LinkedList<WorkerThread> pool;
    private int poolSize;

    private static final Object lock = new Object();

    public WorkerPool(int N) {
        queue = new PriorityBlockingQueue<QueueElement>();
        pool = new LinkedList<WorkerThread>();
        this.poolSize = N;
    }

    public void startPool() {
        synchronized (lock) {
            for (int i = 0; i < poolSize; i++) {
                WorkerThread thread = new WorkerThread("Thread " + i);
                thread.start();
                pool.add(thread);
            }
        }
    }

    public void stopPool() {
        synchronized (lock) {
            Iterator<QueueElement> itq = queue.iterator();
            while(itq.hasNext()) {
                QueueElement el = itq.next();
                if(el != null) {
                    if(el.getWorker() != null)
                        el.getWorker().cancelWorker();
                    itq.remove();
                }
            }

            Iterator<WorkerThread> itp = pool.iterator();
            while(itp.hasNext()) {
                WorkerThread wt = itp.next();
                if(wt != null) {
                    wt.killWorker();
                    wt.interrupt();
                }
                itp.remove();
            }
        }
    }

    private boolean alreadyInPool(QueueElement qe) {
        Iterator<WorkerThread> it = pool.iterator();
        while (it.hasNext()) {
            WorkerThread element = it.next();
            Worker worker = element.getWorker();
            if (worker != null) {
                if (worker.getWorkerIdentifier().equals(qe.getWorker().getWorkerIdentifier()))
                    return true;
            }
        }
        return false;
    }

    private QueueElement alreadyInQueue(QueueElement qe) {
        Iterator<QueueElement> it = queue.iterator();
        while (it.hasNext()) {
            QueueElement element = it.next();
            if (element.getWorker().getWorkerIdentifier()
                    .equals(qe.getWorker().getWorkerIdentifier()))
                return element;
        }
        return null;
    }

    public boolean addWorker(Worker worker, int priority) {
        synchronized (lock) {
            QueueElement qe = new QueueElement(worker, priority);
            if (alreadyInPool(qe)) {
                Log.d(TAG, "[-] same worker already exists in pool");
                return false;
            }
            QueueElement exists = alreadyInQueue(qe);
            if (exists == null) {
                Log.d(TAG, "[+] " + worker.getWorkerIdentifier() + " added to ThreadQueue");
                queue.add(qe);
                return true;
            }
            if (exists.getPriority() < priority) {
                Log.d(TAG, "[-] same thread exists in queue with more priority");
                return false;
            }
            queue.remove(exists);
            Log.d(TAG, "[+] " + worker.getWorkerIdentifier() + " added to ThreadQueue");
            queue.add(qe);
            return true;
        }
    }

    public boolean addWorker(Worker worker) {
        return addWorker(worker, PRIORITY_MIDDLE);
    }

    public void stopWorkers(String protocolIdentifier) {
        synchronized (lock) {
            final List<Worker> ret = new LinkedList<Worker>();
            Iterator<QueueElement> itq = queue.iterator();
            while(itq.hasNext()) {
                QueueElement element = itq.next();
                if(element.getWorker().getProtocolIdentifier().equals(protocolIdentifier)) {
                    Log.d(TAG, "[-] removing worker from queue ("+element.getWorker().getWorkerIdentifier()+")");
                    element.getWorker().cancelWorker();
                    itq.remove();
                }
            }

            Iterator<WorkerThread> itp = pool.iterator();
            while(itp.hasNext()) {
                WorkerThread element = itp.next();
                if(element.getWorker() != null)
                    if(element.getWorker().getProtocolIdentifier().equals(protocolIdentifier)) {
                        Log.d(TAG, "[-] stopping worker from thread ("+element.getWorker().getWorkerIdentifier()+")");
                        element.killWorker();
                    }
            }
        }
    }

    public void stopWorker(String workerID) {
        synchronized (lock) {
            Iterator<QueueElement> itq = queue.iterator();
            while(itq.hasNext()) {
                QueueElement element = itq.next();
                if(element.getWorker().getWorkerIdentifier().equals(workerID)) {
                    Log.d(TAG, "[-] removing worker from queue ("+element.getWorker().getWorkerIdentifier()+")");
                    element.getWorker().cancelWorker();
                    itq.remove();
                }
            }

            Iterator<WorkerThread> itp = pool.iterator();
            while(itp.hasNext()) {
                WorkerThread element = itp.next();
                if(element.getWorker() != null)
                    if(element.getWorker().getWorkerIdentifier().equals(workerID)) {
                        Log.d(TAG, "[-] stopping worker from thread ("+element.getWorker().getWorkerIdentifier()+")");
                        element.getWorker().stopWorker();
                    }
            }
        }
    }

    private class QueueElement implements Comparable{

        private Worker worker;
        private int priority;

        QueueElement(Worker worker, int priority){
            this.worker = worker;
            this.priority = priority;
        }

        public Worker getWorker() {
            return worker;
        }
        public int getPriority() {
            return priority;
        }

        @Override
        public int compareTo(Object obj) {
            if (obj == this)
                return 0;

            if(this.priority < ((QueueElement)obj).getPriority())
                return -1;
            if(this.priority == ((QueueElement)obj).getPriority())
                return 0;
            return 1;
        }
    }

    private class WorkerThread extends Thread {

        private static final String TAG = "WorkerThread";

        private Worker worker;

        WorkerThread(String name) {
            super(name);
            worker = null;
        }

        @Override
        public void run() {
            Log.d(TAG, "[+] "+this.getName()+" Started");

            try {
                while (true) {
                    worker = null;
                    QueueElement element;

                    element = queue.take();

                    if(element == null)
                        continue;

                    Log.d(TAG, "[+] "+this.getName()+" consumes "+element.getWorker().getWorkerIdentifier());
                    this.worker = element.getWorker();
                    worker.startWorker();
                }
            } catch (InterruptedException ignore) {
            }
        }

        public Worker getWorker(){
            return worker;
        }

        public void killWorker() {
            if(worker != null)
                worker.stopWorker();
        }
    }
}