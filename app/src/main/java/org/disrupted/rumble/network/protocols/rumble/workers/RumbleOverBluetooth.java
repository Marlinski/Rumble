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

package org.disrupted.rumble.network.protocols.rumble.workers;

import android.util.Log;

import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServerConnection;
import org.disrupted.rumble.network.protocols.events.NeighbourConnected;
import org.disrupted.rumble.network.protocols.events.NeighbourDisconnected;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClientConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTState;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;

import java.io.IOException;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class RumbleOverBluetooth extends RumbleUnicastChannel {

    private static final String TAG = "RumbleOverBluetooth";

    public RumbleOverBluetooth(RumbleProtocol protocol, BluetoothConnection con) {
        super(protocol, con);
    }

    @Override
    public void cancelWorker() {
        RumbleBTState connectionState = ((RumbleProtocol)protocol).getBTState(((BluetoothConnection)con).getRemoteLinkLayerAddress());
        if(working) {
            Log.e(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
        } else
            connectionState.notConnected();
    }

    @Override
    public void startWorker() {
        super.startWorker();
        if (working)
            return;
        working = true;

        RumbleBTState connectionState = ((RumbleProtocol)protocol).getBTState(((BluetoothConnection)con).getRemoteLinkLayerAddress());

        try {

            if (con instanceof BluetoothClientConnection) {
                if (!connectionState.getState().equals(RumbleBTState.RumbleBluetoothState.CONNECTION_SCHEDULED))
                    throw new RumbleBTState.StateException();

                ((BluetoothClientConnection) con).waitScannerToStop();

                try {
                    connectionState.lock.lock();
                    connectionState.connectionInitiated(getWorkerIdentifier());
                } finally {
                    connectionState.lock.unlock();
                }
            }

            con.connect();

            try {
                connectionState.lock.lock();
                connectionState.connected(getWorkerIdentifier());
            } finally {
                connectionState.lock.unlock();
            }

            /*
             * Bluetooth hack to synchronise the client and server
             * if I don't do this, they sometime fail to connect ? :/ ?
             */
            if (con instanceof BluetoothServerConnection)
                ((BluetoothConnection)con).getOutputStream().write(new byte[]{0},0,1);
            if (con instanceof BluetoothClientConnection)
                ((BluetoothConnection)con).getInputStream().read(new byte[1], 0, 1);

        } catch (RumbleBTState.StateException state) {
            Log.e(TAG, "[-] client connected while trying to connect");
            stopWorker();
            return;
        } catch (LinkLayerConnectionException llce) {
            Log.e(TAG, "[!] FAILED CON: " + getWorkerIdentifier() + " - " + llce.getMessage());
            stopWorker();
            connectionState.notConnected();
            return;
        } catch (IOException io) {
            Log.e(TAG, "[!] FAILED CON: " + getWorkerIdentifier() + " - " + io.getMessage());
            stopWorker();
            connectionState.notConnected();
            return;
        }

        try {
            Log.d(TAG, "[+] connected");
            EventBus.getDefault().post(new NeighbourConnected(
                            ((BluetoothConnection)con).getLinkLayerNeighbour(),
                            this)
            );
            onWorkerConnected();
        } finally {
            Log.d(TAG, "[+] disconnected");
            EventBus.getDefault().post(new NeighbourDisconnected(
                            ((BluetoothConnection)con).getLinkLayerNeighbour(),
                            this)
            );
            stopWorker();
            connectionState.notConnected();
        }
    }

}
