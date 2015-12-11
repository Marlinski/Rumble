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

package org.disrupted.rumble.network.protocols.rumble;

import org.disrupted.rumble.util.Log;

import java.util.concurrent.locks.ReentrantLock;

/**
 * The RumbleStateMachine describe the different state possible when connecting to a peer
 * wether it is done using Bluetooth or TCP.
 *
 *                      +-----------------+
 *                      |  NOT CONNECTED  |
 *                      +-----------------+
 *                         |          |
 *                   connection      connection
 *                    received       initiated
 *                         |          |
 *              +----------+          +-----------+
 *              |                                 |
 *              v                                 v
 *     +---------------------+      +----------------------+
 *     | CONNECTION ACCEPTED |      | CONNECTION SCHEDULED |
 *     +---------------------+      +---------------------+
 *              |      ^              |            ^    |
 *              |      |         connection        |    |
 *              |      |          received         |    |
 *              |      |              |            |    |
 *              |      |              v            |    |
 *              |      |       +============+      |    |
 *              |      |       | is current |      |    |
 *              |      +- yes <   address   > no -+     |
 *              |              |   lower ?  |           |
 *              |              +============+           |
 *              |                                       |
 *              |                                       |
 *              |          +-------------+              |
 *              +--------->|  CONNECTED  |<-------------+
 *                         +-------------+
 *
 *
 * @author Lucien Loiseau
 */
public class RumbleStateMachine {

    private static final String TAG = "RumbleStateMachine";

    public static enum RumbleState {
        NOT_CONNECTED, CONNECTION_SCHEDULED, CONNECTION_ACCEPTED, CONNECTED
    }

    public  final ReentrantLock lock = new ReentrantLock();
    private RumbleState state;
    private String workerID;

    public RumbleStateMachine() {
        this.state = RumbleState.NOT_CONNECTED;
        this.workerID = null;
    }

    public String printState() {
        switch (state) {
            case NOT_CONNECTED: return "NOT CONNECTED";
            case CONNECTION_SCHEDULED: return "CONNECTION SCHEDULED";
            case CONNECTION_ACCEPTED: return "CONNECTION ACCEPTED";
            case CONNECTED: return "CONNECTED";
            default: return "####";
        }
    }

    /*
     * goTo the CONNECTION SCHEDULED state which happens when we put a worker on the pool
     * The workerID is then stored as it may be cancelled under certain circumstances
     * It throws a StateException if previous state is different than NOT_CONNECTED
     */
    public void connectionScheduled(String workerID) throws StateException {
        String previous = printState();
        switch (state) {
            case CONNECTED:
            case CONNECTION_SCHEDULED:
            case CONNECTION_ACCEPTED:
                throw new StateException();
            case NOT_CONNECTED:
            default:
                this.workerID = workerID;
                state = RumbleState.CONNECTION_SCHEDULED;
                Log.d(TAG, previous+" -> "+printState()+ " ("+this.workerID+")");
        }
    }

    /*
     * goTo the CONNECTION ACCEPTED state which happens when a Server receive
     * a connection (accept() returns). The workerID is then stored as it may be
     * cancelled under certain circumstances.
     * It can only happen from the NOT_CONNECTED or CONNECTION_INITIATED state.
     * It throws a StateException otherwise
     */
    public void connectionAccepted(String workerID) throws StateException {
        String previous = printState();
        switch (state) {
            case CONNECTED:
            case CONNECTION_ACCEPTED:
                throw new StateException();
            case CONNECTION_SCHEDULED:
            case NOT_CONNECTED:
            default:
                this.workerID = workerID;
                state = RumbleState.CONNECTION_ACCEPTED;
                Log.d(TAG, previous+" -> "+printState()+ " ("+this.workerID+")");
        }
    }

    /*
     * goTo the CONNECTED state which happens when a connection connect()
     * It can only happen from an intermediary state like CONNECTION_INITIATED or
     * CONNECTED_ACCEPTED. It throws a StateException otherwise
     */
    public void connected(String workerID) throws StateException {
        String previous = printState();
        switch (state) {
            case NOT_CONNECTED:
            case CONNECTED:
                throw new StateException();
            case CONNECTION_SCHEDULED:
            case CONNECTION_ACCEPTED:
            default:
                this.workerID = workerID;
                state = RumbleState.CONNECTED;
                Log.d(TAG, previous+" -> "+printState()+ " ("+this.workerID+")");
        }
    }

    /*
     * goTo the NOT_CONNECTED state which can happen from any state
     *    - when a Connection disconnect()
     *    - when the intermediary state has been cancelled
     */
    public void notConnected() {
        String previous = printState();
        switch (state) {
            default:
                state = RumbleState.NOT_CONNECTED;
                this.workerID = null;
                Log.d(TAG, previous+" -> "+printState());
        }
    }

    public RumbleState getState() {
        return state;
    }
    public String getWorkerID()  throws StateException{
        return this.workerID;
    }
    public static class StateException extends Exception {
    }
}
