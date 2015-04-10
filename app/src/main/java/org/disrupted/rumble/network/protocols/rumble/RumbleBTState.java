package org.disrupted.rumble.network.protocols.rumble;

import android.util.Log;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Marlinski
 */
public class RumbleBTState {

    private static final String TAG = "RumbleBTState";

    public static enum RumbleBluetoothState {
        NOT_CONNECTED, CONNECTION_INITIATED, CONNECTION_ACCEPTED, CONNECTED;
    }

    public final ReentrantLock lockWorker = new ReentrantLock();
    private final Object lockRumbleBTState = new Object();
    private RumbleBluetoothState state;
    private String workerID;

    public RumbleBTState() {
        this.state = RumbleBluetoothState.NOT_CONNECTED;
        this.workerID = null;
    }

    public String printState() {
        switch (state) {
            case NOT_CONNECTED: return "NOT CONNECTED";
            case CONNECTION_INITIATED: return "CONNECTION INITIATED";
            case CONNECTION_ACCEPTED: return "CONNECTION ACCEPTED";
            case CONNECTED: return "CONNECTED";
            default: return "####";
        }
    }

    /*
     * goTo the CONNECTION INITIATED state which happens when we initiate a connection
     * (most probably from the BluetoothLinkLayer.connectTo method).
     * The workerID is then stored as it may be cancelled under certain circumstances.
     * It throws a StateException if previous state is different than NOT_CONNECTED
     */
    public void connectionInitiated(String workerID) throws StateException {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            switch (state) {
                case CONNECTED:
                case CONNECTION_INITIATED:
                case CONNECTION_ACCEPTED:
                    throw new StateException();
                case NOT_CONNECTED:
                default:
                    this.workerID = workerID;
                    state = RumbleBluetoothState.CONNECTION_INITIATED;
                    Log.d(TAG, previous+" -> "+printState()+ " ("+this.workerID+")");
            }
        }
    }

    /*
     * goTo the CONNECTION ACCEPTED state which happens when RumbleBTServer receive
     * a connection (accept() returns). The workerID is then stored as it may be
     * cancelled under certain circumstances.
     * It can only happen from the NOT_CONNECTED or CONNECTION_INITIATED state.
     * It throws a StateException otherwise
     */
    public void connectionAccepted(String workerID) throws StateException {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            switch (state) {
                case CONNECTED:
                case CONNECTION_ACCEPTED:
                    throw new StateException();
                case CONNECTION_INITIATED:
                case NOT_CONNECTED:
                default:
                    this.workerID = workerID;
                    state = RumbleBluetoothState.CONNECTION_ACCEPTED;
                    Log.d(TAG, previous+" -> "+printState()+ " ("+this.workerID+")");
            }
        }
    }

    /*
     * goTo the CONNECTED state which happens when a BluetoothConnection connect()
     * (wether it is as a BluetoothClient or as a BluetoothServerConnection)
     * It can only happen from an intermediary state like CONNECTION_INITIATED or
     * CONNECTED_ACCEPTED. It throws a StateException otherwise
     */
    public void connected(String workerID) throws StateException {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            switch (state) {
                case NOT_CONNECTED:
                case CONNECTED:
                    throw new StateException();
                case CONNECTION_INITIATED:
                case CONNECTION_ACCEPTED:
                default:
                    this.workerID = workerID;
                    state = RumbleBluetoothState.CONNECTED;
                    Log.d(TAG, previous+" -> "+printState()+ " ("+this.workerID+")");
            }
        }
    }

    /*
     * goTo the NOT_CONNECTED state which can happen from any state
     *    - when a BluetoothConnection disconnect()
     *    - when the intermediary state has been cancelled
     */
    public void notConnected() {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            switch (state) {
                default:
                    state = RumbleBluetoothState.NOT_CONNECTED;
                    this.workerID = null;
                    Log.d(TAG, previous+" -> "+printState());
            }
        }
    }

    public RumbleBluetoothState getState() {
        return state;
    }
    public String getConnectionInitiatedWorkerID()  throws StateException{
        if(state == RumbleBluetoothState.CONNECTION_INITIATED)
            return this.workerID;
        throw new StateException();
    }
    public String getConnectionAcceptedWorkerID() throws StateException{
        if(state == RumbleBluetoothState.CONNECTION_ACCEPTED)
            return this.workerID;
        throw new StateException();
    }

    public class StateException extends Exception {
    }
}
