package org.disrupted.rumble.network.protocols.firechat;

import org.disrupted.rumble.util.Log;

/**
 * @author Lucien Loiseau
 */
public class FirechatBTState {

    private static final String TAG = "FirechatBTState";

    public static enum FirechatBluetoothState {
        NOT_CONNECTED, CONNECTION_INITIATED, CONNECTED;
    }


    private final Object lockRumbleBTState = new Object();
    private FirechatBluetoothState state;

    public FirechatBTState() {
        this.state = FirechatBluetoothState.NOT_CONNECTED;
    }

    public String printState() {
        switch (state) {
            case NOT_CONNECTED: return "NOT CONNECTED";
            case CONNECTION_INITIATED: return "CONNECTION INITIATED";
            case CONNECTED: return "CONNECTED";
            default: return "####";
        }
    }

    public void connectionInitiated() throws StateException {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            switch (state) {
                case CONNECTED:
                case CONNECTION_INITIATED:
                    throw new StateException();
                case NOT_CONNECTED:
                default:
                    state = FirechatBluetoothState.CONNECTION_INITIATED;
                    Log.d(TAG, previous+" -> "+printState());
            }
        }
    }

    public void connected(String workerID) throws StateException {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            switch (state) {
                case NOT_CONNECTED:
                case CONNECTED:
                    throw new StateException();
                case CONNECTION_INITIATED:
                default:
                    state = FirechatBluetoothState.CONNECTED;
                    Log.d(TAG, previous+" -> "+printState());
            }
        }
    }

    public void notConnected() {
        synchronized (lockRumbleBTState) {
            String previous = printState();
            state = FirechatBluetoothState.NOT_CONNECTED;
            Log.d(TAG, previous+" -> "+printState());
        }
    }

    public FirechatBluetoothState getState() {
        return state;
    }

    public class StateException extends Exception {
    }
}
