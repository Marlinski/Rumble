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

import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothUtil;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.events.ChannelDisconnected;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStarted;
import org.disrupted.rumble.network.linklayer.events.LinkLayerStopped;
import org.disrupted.rumble.network.events.NeighbourReachable;
import org.disrupted.rumble.network.events.NeighbourUnreachable;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClientConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPClientConnection;
import org.disrupted.rumble.network.linklayer.wifi.TCP.TCPConnection;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;
import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.Worker;
import org.disrupted.rumble.network.protocols.rumble.workers.RumbleBTServer;
import org.disrupted.rumble.network.protocols.rumble.workers.RumbleTCPServer;
import org.disrupted.rumble.network.protocols.rumble.workers.RumbleUDPMulticastScanner;
import org.disrupted.rumble.network.protocols.rumble.workers.RumbleUnicastChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class RumbleProtocol implements Protocol {

    public static final String TAG = "RumbleProtocol";
    public static final String protocolID = "Rumble";
    public static final String RUMBLE_BLUETOOTH_PREFIX = "Rumble-";

    private static final Object lock = new Object();
    public static RumbleProtocol instance = null;

    private final NetworkCoordinator networkCoordinator;
    private boolean started;

    // Scanner to discover peer whenever wifi is available
    RumbleUDPMulticastScanner scanner;

    //conState holds the Connection State of Bluetooth and TCP connection
    private Map<String, RumbleStateMachine> conState;
    public RumbleStateMachine getState(String linkLayerAddress) {
        synchronized (lock) {
            RumbleStateMachine state = conState.get(linkLayerAddress);
            if (state == null) {
                state = new RumbleStateMachine();
                conState.put(linkLayerAddress, state);
            }
            return state;
        }
    }

    @Override
    public NetworkCoordinator getNetworkCoordinator() {
        return networkCoordinator;
    }

    public static RumbleProtocol getInstance(NetworkCoordinator networkCoordinator) {
        synchronized (lock) {
            if(instance == null)
                instance = new RumbleProtocol(networkCoordinator);
            return instance;
        }
    }

    private RumbleProtocol(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
        conState = new HashMap<String, RumbleStateMachine>();
        started = false;
        scanner = null;
    }

    @Override
    public int getProtocolPriority() {
        return PROTOCOL_HIGH_PRIORITY;
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

        Log.d(TAG, "[+] Rumble Protocol started");

        EventBus.getDefault().register(this);
    }

    @Override
    public void protocolStop() {
        if(!started)
            return;
        started = false;

        Log.d(TAG, "[-] Rumble Protocol stopped");

        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);

        networkCoordinator.stopWorkers(BluetoothLinkLayerAdapter.LinkLayerIdentifier, protocolID);
        networkCoordinator.stopWorkers(WifiLinkLayerAdapter.LinkLayerIdentifier, protocolID);
        conState.clear();
    }

    @Override
    public void onEvent(LinkLayerStarted event) {
        if(!started)
            return;

        if(event.linkLayerIdentifier.equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier)) {
            UUID uuid;
            String macAddress = BluetoothUtil.getBluetoothMacAddress();
            if(macAddress == null)
                uuid = RumbleBTServer.RUMBLE_BT_UUID_128_DEFAULT;
            else
                uuid = UUID.fromString(RumbleBTServer.RUMBLE_BT_UUID_128_PREFIX+macAddress.replaceAll(":",""));

            Worker BTServer = new RumbleBTServer(this, networkCoordinator,uuid);
            networkCoordinator.addWorker(BTServer);

            // backward compatibility
            /*
            if(!uuid.equals(RumbleBTServer.RUMBLE_BT_UUID_128_DEFAULT)) {
                Worker BTServerOld = new RumbleBTServer(this, networkCoordinator, RumbleBTServer.RUMBLE_BT_UUID_128_DEFAULT);
                networkCoordinator.addWorker(BTServer);
            }
            */
        }

        if(event.linkLayerIdentifier.equals(WifiLinkLayerAdapter.LinkLayerIdentifier)) {
            Worker TCPServer = new RumbleTCPServer(this, networkCoordinator);
            networkCoordinator.addWorker(TCPServer);

            scanner = new RumbleUDPMulticastScanner();
            scanner.startScanner();
            networkCoordinator.addScanner(scanner);
        }
    }

    @Override
    public void onEvent(LinkLayerStopped event) {
        if(!started)
            return;

        networkCoordinator.stopWorkers(event.linkLayerIdentifier, protocolID);
        if(event.linkLayerIdentifier.equals(WifiLinkLayerAdapter.LinkLayerIdentifier)) {
            if(scanner != null) {
                networkCoordinator.delScanner(scanner);
                scanner.stopScanner();
                scanner = null;
            }
        }
    }

    @Override
    public void onEvent(NeighbourReachable event) {
        if(!started)
            return;
        if(event.neighbour.isLocal())
            return;
        openChannel(event.neighbour);
    }

    @Override
    public void onEvent(NeighbourUnreachable event) {
        if(!started)
            return;
        conState.remove(event.neighbour.getLinkLayerAddress());
    }

    public void onEvent(ChannelDisconnected event) {
        if(event.channel.getProtocolIdentifier().equals(getProtocolIdentifier()) && event.error) {
            openChannel(event.neighbour);
        }
    }

    private void openChannel(LinkLayerNeighbour neighbour) {
        if (neighbour instanceof BluetoothNeighbour) {
            BluetoothNeighbour btn = (BluetoothNeighbour)neighbour;
            if(btn.getBluetoothDeviceName() == null)
                return;
            if(!btn.getBluetoothDeviceName().startsWith(RUMBLE_BLUETOOTH_PREFIX))
                return;
            try {
                BluetoothConnection con = new BluetoothClientConnection(
                        neighbour.getLinkLayerAddress(),
                        UUID.fromString(RumbleBTServer.RUMBLE_BT_UUID_128_PREFIX+
                                neighbour.getLinkLayerAddress().replaceAll(":","")),
                        RumbleBTServer.RUMBLE_BT_STR,
                        false);
                Worker rumbleOverBluetooth = new RumbleUnicastChannel(this, con);
                getState(neighbour.getLinkLayerAddress()).connectionScheduled(rumbleOverBluetooth.getWorkerIdentifier());
                networkCoordinator.addWorker(rumbleOverBluetooth);
            } catch(RumbleStateMachine.StateException ignore) {
                //Log.d(TAG, neighbour.getLinkLayerAddress() + " state is not disconnected: " + getBTState(neighbour.getLinkLayerAddress()).printState());
            }
        }

        if (neighbour instanceof WifiNeighbour) {
            try {
                TCPConnection con = new TCPClientConnection(
                        neighbour.getLinkLayerAddress(),
                        RumbleTCPServer.RUMBLE_TCP_PORT
                );
                Worker rumbleOverTCP = new RumbleUnicastChannel(this, con);
                getState(neighbour.getLinkLayerAddress()).connectionScheduled(rumbleOverTCP.getWorkerIdentifier());
                networkCoordinator.addWorker(rumbleOverTCP);
            } catch(RumbleStateMachine.StateException ignore) {
                //Log.d(TAG, neighbour.getLinkLayerAddress() + " state is not disconnected: " + getBTState(neighbour.getLinkLayerAddress()).printState());
            }
        }
    }
}
