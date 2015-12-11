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

package org.disrupted.rumble.database;

import org.disrupted.rumble.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * DatabaseExecutor
 *
 * Every I/O operation on the Database are done by one thread only: the executorThread
 * This ensures that the database will not be accessed by multiple thread concurently
 * Also each operation are done asynchronously and a QueryEvent is thrown when the query
 * is done
 *
 * Operation on the database are classified into "write" operation and "read" operation.
 *    - Write operation results in a callback being called with a boolean as parameter
 *      To request a write operation, one must call
 *
 *              addWritableTask(WritableQuery query, WritableQueryFinished callback)
 *
 *    - Read operation results a ReadableQueryCallback to be called
 *      To request a read operation, one must call
 *
 *               addReadableTask(ReadableQuery query, ReadableQueryFinished callback)
 *
 * @author Lucien Loiseau
 */
public class DatabaseExecutor {

    private static final String TAG = "DatabaseExecutor";

    private BlockingQueue<Runnable> queryQueue;
    private Thread executorThread;

    private static final Object lock = new Object();
    private boolean running;

    public interface WritableQuery {
        public boolean write();
    }
    public interface WritableQueryCallback {
        public void onWritableQueryFinished(boolean success);
    }
    public interface ReadableQuery {
        public Object read();
    }
    public interface ReadableQueryCallback {
        public void onReadableQueryFinished(Object object);
    }

    public DatabaseExecutor() {
        executorThread = null;
        queryQueue = new LinkedBlockingQueue<Runnable>();
        startExecutor();
    }

    @Override
    protected void finalize() throws Throwable {
        stopExecutor();
        super.finalize();
    }

    private void startExecutor() {
        if(running)
            return;
        running = true;

        executorThread = new Thread() {
            @Override
            public synchronized void run() {
                Log.d(TAG, "[+] Database executor started");
                try {
                    while (true) {
                        Runnable task = queryQueue.take();
                        task.run();
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG, "[!] Executor thread has stopped");
                }
            }
        };
        executorThread.start();
    }

    public void stopExecutor() {
        if(!running)
            return;
        running = false;

        if (executorThread != null)
            executorThread.interrupt();
        executorThread = null;
        queryQueue.clear();
    }

    public boolean addQuery(final WritableQuery query, final WritableQueryCallback callback) {
        if(!running)
            return false;
        synchronized (lock) {
            queryQueue.add(new Runnable() {
                @Override
                public void run() {
                    boolean success = query.write();
                    if(callback != null)
                        callback.onWritableQueryFinished(success);
                }
            });
        }
        return true;
    }

    public boolean addQuery(final ReadableQuery query, final ReadableQueryCallback callback) {
        if(!running)
            return false;
        synchronized (lock) {
            queryQueue.add(new Runnable() {
                @Override
                public void run() {
                    Object object = query.read();
                    if(callback != null)
                        callback.onReadableQueryFinished(object);

                }
            });
        }
        return true;
    }

}
