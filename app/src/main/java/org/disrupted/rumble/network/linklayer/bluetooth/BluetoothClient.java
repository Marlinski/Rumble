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

package org.disrupted.rumble.network.linklayer.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.disrupted.rumble.network.events.BluetoothScanEnded;
import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.protocols.Protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import de.greenrobot.event.EventBus;

/**
 * BluetoothClient tries to establish a connection with a remote Bluetooth Device
 * If the BluetoothAdapter is performing a scan procedure, it waits for it to finish
 * in order to avoid connection issue.
 *
 * @author Marlinski
 */
public class BluetoothClient extends Connection {

    private static final String TAG = "BluetoothClient";

    private UUID bt_service_uuid;
    private String bt_service_name;

    private final CountDownLatch latch = new CountDownLatch(1);

    private BluetoothDevice mmBluetoothDevice;
    private boolean secureSocket;
    private BluetoothSocket mmConnectedSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private boolean isBeingKilled;

    public BluetoothClient(String remoteMacAddress, UUID uuid, String name, boolean secure, Protocol message, ConnectionCallback callback){
        super(remoteMacAddress, message, BluetoothLinkLayerAdapter.LinkLayerIdentifier, callback);
        this.bt_service_uuid = uuid;
        this.bt_service_name = name;
        this.secureSocket = secure;
        this.connectionID = "ConnectTO: "+this.macAddress+":"+bt_service_uuid.toString();
        this.mmConnectedSocket = null;
        this.inputStream = null;
        this.outputStream = null;
        this.isBeingKilled = false;
    }

    @Override
    public void run() {
        try {
            NetworkCoordinator networkCoordinator = NetworkCoordinator.getInstance();
            mmBluetoothDevice = BluetoothUtil.getBluetoothAdapter(networkCoordinator).getRemoteDevice(this.macAddress);
            if(mmBluetoothDevice == null) throw new Exception("[!] no remote bluetooth device");

            if(BluetoothUtil.getBluetoothAdapter(networkCoordinator).isDiscovering()) {
                EventBus.getDefault().register(this);
                latch.await();
                EventBus.getDefault().unregister(this);
            }

            if(secureSocket)
                mmConnectedSocket = mmBluetoothDevice.createRfcommSocketToServiceRecord(bt_service_uuid);
            else
                mmConnectedSocket = mmBluetoothDevice.createInsecureRfcommSocketToServiceRecord(bt_service_uuid);

            if(mmConnectedSocket == null) throw new Exception("[!] Unable to connect to"+macAddress+" to service "+ bt_service_uuid.toString());
            mmConnectedSocket.connect();
        } catch (Exception e) {
            onConnectionFailed(e.toString());
            return;
        }


        try {
            inputStream = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        } catch (IOException e) {
            onConnectionFailed("Cannot get Input/Output stream from Bluetooth Socket");
            return;
        }

        onConnectionEstablished(macAddress);
        protocol.onConnected(inputStream,outputStream);

        if(!isBeingKilled)
            kill();
    }

    @Override
    public void kill() {
        this.isBeingKilled = true;
        if(protocol.isRunning()) {
            /*
            if (inputStream != null) {
                try {inputStream.close();} catch (Exception ignore) {}
                inputStream = null;
            }

            if (outputStream != null) {
                try {outputStream.close();} catch (Exception ignore) {}
                outputStream = null;
            }
            */
            if (mmConnectedSocket != null) {
                try {mmConnectedSocket.close();} catch (Exception ignore) {}
                mmConnectedSocket = null;
            }
            onConnectionEnded(macAddress);
        }
    }

    public void onEvent(BluetoothScanEnded scanEnded) {
        latch.countDown();
    }

}
