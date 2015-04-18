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

package org.disrupted.rumble.network.protocols.firechat;

import android.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.linklayer.events.NeighbourReachable;
import org.disrupted.rumble.network.linklayer.events.NeighbourUnreachable;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClientConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.UDPNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;
import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.protocols.Worker;
import org.disrupted.rumble.network.protocols.firechat.workers.FirechatBTServer;
import org.disrupted.rumble.network.protocols.firechat.workers.FirechatOverBluetooth;
import org.disrupted.rumble.network.protocols.firechat.workers.FirechatOverUDPMulticast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FirechatProtocol implements Protocol {

    public static final String TAG = "FirechatProtocol";

    public static final String protocolID = "Firechat";

    /*
     * Firechat Bluetooth Configuration
     */
    //public static final UUID   FIRECHAT_BT_UUID_128 = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    public static final UUID   FIRECHAT_BT_UUID_128 = UUID.fromString("249916a5-4173-46e9-9320-36a8c1e8c487");
    public static final String FIRECHAT_BT_STR      = "FireChat";

    private static final Object lock = new Object();
    private final NetworkCoordinator networkCoordinator;
    private boolean started;

    private Map<String, FirechatBTState> bluetoothState;

    public FirechatProtocol(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
        bluetoothState = new HashMap<String, FirechatBTState>();
        started = false;
    }

    public FirechatBTState getBTState(String macAddress) {
        synchronized (lock) {
            FirechatBTState state = bluetoothState.get(macAddress);
            if (state == null) {
                state = new FirechatBTState();
                bluetoothState.put(macAddress, state);
            }
            return state;
        }
    }

    @Override
    public String getProtocolIdentifier() {
        return protocolID;
    }

    @Override
    public void protocolStart() {
        if(started)
            return;

        started = true;
        EventBus.getDefault().register(this);
    }

    @Override
    public void protocolStop() {
        if(!started)
            return;

        EventBus.getDefault().unregister(this);
        networkCoordinator.stopWorkers(BluetoothLinkLayerAdapter.LinkLayerIdentifier, protocolID);
        networkCoordinator.stopWorkers(WifiManagedLinkLayerAdapter.LinkLayerIdentifier, protocolID);
        started = false;
    }

    @Override
    public void onEvent(LinkLayerStarted event) {
        if(!started)
            return;

        if(event.linkLayerIdentifier.equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier)) {
            Worker BTServer = new FirechatBTServer(this, networkCoordinator);
            networkCoordinator.addWorker(BTServer);
        }

        if(event.linkLayerIdentifier.equals(WifiManagedLinkLayerAdapter.LinkLayerIdentifier)) {
            Worker firechatOverUDP = new FirechatOverUDPMulticast();
            networkCoordinator.addWorker(firechatOverUDP);
        }
    }

    @Override
    public void onEvent(LinkLayerStopped event) {
        if(!started)
            return;

        networkCoordinator.stopWorkers(event.linkLayerIdentifier, protocolID);
    }

    @Override
    public void onEvent(NeighbourReachable event) {
        if(!started)
            return;

        LinkLayerNeighbour neighbour = event.neighbour;
        if(neighbour instanceof BluetoothNeighbour) {
            try {
                getBTState(neighbour.getLinkLayerAddress()).connectionInitiated();
                BluetoothConnection con = new BluetoothClientConnection(
                        neighbour.getLinkLayerAddress(),
                        FIRECHAT_BT_UUID_128,
                        FIRECHAT_BT_STR,
                        false);
                FirechatOverBluetooth firechatOverBluetooth = new FirechatOverBluetooth(this, con);
                networkCoordinator.addWorker(firechatOverBluetooth);
            } catch (FirechatBTState.StateException ignore) {
                Log.d(TAG, neighbour.getLinkLayerAddress()+" state error: "+getBTState(neighbour.getLinkLayerAddress()).printState());
            }

        }

        if(neighbour instanceof UDPNeighbour) {
          /**
           * We don't need a worker to manage this specific neighbour
           * because in Multicast operation, every neighbour are being managed
           * by the same worker.
           */
        }
    }

    @Override
    public void onEvent(NeighbourUnreachable event) {
        if(!started)
            return;

        LinkLayerNeighbour neighbour = event.neighbour;

        if(neighbour instanceof BluetoothNeighbour) {
            /**
             * ignore because sometimes the BluetoothScanner may not detect the neighbour
             * while still being connected to it.
             * If the neighbour is indeed disconnected, the connection will drop by itself.
             * todo maybe add a timeout just in case ?
             */
        }

        if(neighbour instanceof UDPNeighbour) {
            /**
             * Ignore because only one worker anyway
             */
        }
    }
}
