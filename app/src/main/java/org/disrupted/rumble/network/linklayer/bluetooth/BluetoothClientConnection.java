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
import android.content.IntentFilter;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.events.BluetoothScanEnded;
import org.disrupted.rumble.network.linklayer.exception.ConnectionFailedException;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.InterruptedLinkLayerConnection;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.NoRemoteBluetoothDevice;
import org.disrupted.rumble.network.linklayer.exception.NullSocketException;

import java.io.IOException;
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
public class BluetoothClientConnection extends BluetoothConnection {

    private static final String TAG = "BluetoothClient";

    protected UUID   bt_service_uuid;
    protected String bt_service_name;

    private final CountDownLatch latch = new CountDownLatch(1);

    public BluetoothClientConnection(String remoteMacAddress, UUID uuid, String name, boolean secure){
        super(remoteMacAddress);
        this.bt_service_uuid = uuid;
        this.bt_service_name = name;
        this.secureSocket = secure;
        this.registered = false;
    }

    @Override
    public String getConnectionID() {
        return "Bluetooth Client: "+remoteMacAddress;
    }

    @Override
    public void connect() throws LinkLayerConnectionException {

        mmBluetoothDevice = BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext()).getRemoteDevice(this.remoteMacAddress);
        if (mmBluetoothDevice == null) throw new NoRemoteBluetoothDevice();

        if (BluetoothUtil.getBluetoothAdapter(RumbleApplication.getContext()).isDiscovering()) {
                EventBus.getDefault().register(this);
                try {
                    latch.await();
                } catch(InterruptedException e) {
                    throw new InterruptedLinkLayerConnection();
                }
                EventBus.getDefault().unregister(this);
        }

        try {
            if (secureSocket)
                mmConnectedSocket = mmBluetoothDevice.createRfcommSocketToServiceRecord(bt_service_uuid);
            else
                mmConnectedSocket = mmBluetoothDevice.createInsecureRfcommSocketToServiceRecord(bt_service_uuid);

            if (mmConnectedSocket == null)
                throw new NullSocketException();

            mmConnectedSocket.connect();
        }catch (IOException e) {
            throw new ConnectionFailedException(remoteMacAddress+" "+bt_service_uuid.toString());
        }

        try {
            inputStream = mmConnectedSocket.getInputStream();
            outputStream = mmConnectedSocket.getOutputStream();
        } catch (IOException e) {
            throw new InputOutputStreamException();
        }

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        RumbleApplication.getContext().registerReceiver(mReceiver, filter);
        registered = true;

    }

    /*
         * todo: it is possible that if the user stops everything it may keep a locking state
         * we don't want to connect while we are discovering cause it mess with the bluetooth
         * This one unlock the latch locked when trying to connect
         */
    public void onEvent(BluetoothScanEnded scanEnded) {
        latch.countDown();
    }


}
