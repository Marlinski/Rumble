/*
 * Copyright (C) 2014 Disrupted Systems
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.protocols.rumble.workers;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.Scanner;
import org.disrupted.rumble.network.linklayer.events.NeighbourReachable;
import org.disrupted.rumble.network.linklayer.events.NeighbourUnreachable;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.UDPMulticastConnection;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.LinkedHashSet;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class RumbleUDPMulticastScanner extends HandlerThread implements Scanner {

    public static final String TAG = "RumbleUDPMulticastScanner";

    public static final int    MULTICAST_UDP_PORT = 9715;
    public static final String MULTICAST_ADDRESS = "239.192.0.0";
    public static final int    PACKET_SIZE = 2048;

    private UDPMulticastConnection con;

    private enum ScanningState {
        SCANNING_OFF, SCANNING_ON,
    }
    private ScanningState scanningState;
    private HashSet<WifiNeighbour>  wifiNeighborhood;
    private static Object lock = new Object();

    private Handler handler;
    private static final int BEACON_TIME = 1000;


    public RumbleUDPMulticastScanner() {
        super(TAG);
        super.start();
        scanningState = ScanningState.SCANNING_OFF;
        wifiNeighborhood = null;
    }

    @Override
    protected void finalize() throws Throwable {
        super.quit();
        super.finalize();
    }

    @Override
    public void startScanner() {
        synchronized (lock) {
            if (scanningState.equals(ScanningState.SCANNING_ON))
                return;
            scanningState = ScanningState.SCANNING_ON;

            handler = new Handler(getLooper());

            try {
                con = new UDPMulticastConnection(
                        MULTICAST_UDP_PORT,
                        MULTICAST_ADDRESS
                );
                con.connect();
            } catch (LinkLayerConnectionException e) {
                return;
            }

            Log.d(TAG, "[+] ----- Rumble Scanner started -----");
            wifiNeighborhood = new LinkedHashSet<WifiNeighbour>();
            receiverThread.start();
            sendBeacon();
        }
    }

    @Override
    public void stopScanner() {
        synchronized (lock) {
            if (scanningState.equals(ScanningState.SCANNING_OFF))
                return;
            scanningState = ScanningState.SCANNING_OFF;

            Log.d(TAG, "[+] ----- Rumble Scanner stopped -----");
            handler.removeCallbacksAndMessages(null);

            if (wifiNeighborhood != null)
                wifiNeighborhood.clear();
            wifiNeighborhood = null;

            try {
                con.disconnect();
            } catch (LinkLayerConnectionException ignore) {
            }
        }
    }

    @Override
    public boolean isScanning() {
        //return scanningState.equals(ScanningState.SCANNING_ON);
        return false;
    }

    @Override
    public void forceDiscovery() {
        // nothing here
    }

    @Override
    public HashSet<LinkLayerNeighbour> getNeighbourList() {
        return null;
    }

    public void sendBeacon() {
        try {
            byte[] buffer = new byte[10];
            con.send(buffer);
        } catch( UDPMulticastSocketException e) {
        } catch( IOException e) {
        }

        handler.postDelayed(scheduleBeaconFires, BEACON_TIME);
    }

    Runnable scheduleBeaconFires = new Runnable() {
        @Override
        public void run() {
            synchronized (lock) {
                if (scanningState.equals(ScanningState.SCANNING_OFF))
                    return;
                sendBeacon();
            }
        }
    };

    Thread receiverThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while(scanningState.equals(ScanningState.SCANNING_ON)) {
                byte[] buffer = new byte[PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer,  PACKET_SIZE);
                try {
                    con.receive(packet);
                    Log.d(TAG, "Packet Received, neighbour ??");
                } catch(UDPMulticastSocketException e) {
                    stopScanner();
                    return;
                } catch( IOException e) {
                    stopScanner();
                    return;
                }
            }
        }
    });




}
