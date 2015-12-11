/*
 * Copyright (C) 2014 Lucien Loiseau
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
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.events.ScannerNeighbourSensed;
import org.disrupted.rumble.network.events.ScannerNeighbourTimeout;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.Scanner;
import org.disrupted.rumble.network.linklayer.events.AccessPointEnabled;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.UDP.UDPMulticastConnection;
import org.disrupted.rumble.network.linklayer.wifi.WifiLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.WifiUtil;
import org.disrupted.rumble.network.events.ChannelDisconnected;

import java.io.IOException;
import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class RumbleUDPMulticastScanner extends HandlerThread implements Scanner {

    public static final String TAG = "RumbleUDPScanner";

    public static final String RUMBLE_FINGERPRINT = "SD8aw874gaKFSuiy";
    public static final byte   RUMBLE_VERSION = 0x01;

    public static final int    MULTICAST_UDP_PORT = 9715;
    public static final String MULTICAST_ADDRESS = "239.192.0.0";
    public static final int    PACKET_SIZE = 2048;

    private static final int BEACON_TIME       = 5000;
    private static final int NEIGHBOUR_TIMEOUT = 10000;

    private UDPMulticastConnection con;

    private enum ScanningState {
        SCANNING_OFF, SCANNING_ON,
    }
    private ScanningState scanningState;
    private static ReentrantLock lock = new ReentrantLock();

    private Handler handler;
    private HashSet<WifiNeighbour>  wifiNeighborhood;
    private Map<WifiNeighbour, Runnable> timeouts;


    public RumbleUDPMulticastScanner() {
        super(TAG);
        super.start();
        scanningState = ScanningState.SCANNING_OFF;
        wifiNeighborhood = null;
        timeouts = new HashMap<WifiNeighbour, Runnable>();
    }

    @Override
    protected void finalize() throws Throwable {
        super.quit();
        super.finalize();
    }

    @Override
    public void startScanner() {
        lock.lock();
        try {
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
                scanningState = ScanningState.SCANNING_OFF;
                return;
            }

            Log.d(TAG, "[+] ----- Rumble UDP Scanner started -----");
            wifiNeighborhood = new LinkedHashSet<WifiNeighbour>();
            if(!EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().register(this);
            receiverThread.start();
            sendBeacon();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stopScanner() {
        lock.lock();
        try {
            if (scanningState.equals(ScanningState.SCANNING_OFF))
                return;
            scanningState = ScanningState.SCANNING_OFF;

            for(Map.Entry<WifiNeighbour, Runnable> entry : timeouts.entrySet()) {
                handler.removeCallbacks(entry.getValue());
            }

            // we disconnect the socket
            try {
                con.disconnect();
            } catch (LinkLayerConnectionException ignore) {
            }

            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
            Log.d(TAG, "[-] ----- Rumble UDP Scanner stopped -----");

            if (wifiNeighborhood != null)
                wifiNeighborhood.clear();
            wifiNeighborhood = null;
        } finally {
            lock.unlock();
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
        return new HashSet<LinkLayerNeighbour>(wifiNeighborhood);
    }


    /*
     * The Beacon is a very small UDP packet carrying a ProtocolVersion and
     * a Fingerprint :
     *
     * +---------------------------------+------------+
     * |          FINGERPRINT            |   Version  |
     * +---------------------------------+------------+
     *             16 bytes                   1 byte
     */
    public void sendBeacon() {
        try {
            byte[] buffer = new byte[17];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.put(RUMBLE_FINGERPRINT.getBytes(), 0, 16);
            byteBuffer.put(RUMBLE_VERSION);
            con.send(byteBuffer.array());
        } catch( UDPMulticastSocketException e) {
        } catch( IOException e) {
        }

        handler.postDelayed(scheduleBeaconFires, BEACON_TIME);
    }

    Runnable scheduleBeaconFires = new Runnable() {
        @Override
        public void run() {
            lock.lock();
            try {
                if (scanningState.equals(ScanningState.SCANNING_OFF))
                    return;
                sendBeacon();
            } finally {
                lock.unlock();
            }
        }
    };

    Thread receiverThread = new Thread(new Runnable() {
        @Override
        public void run() {
            try {
                while(scanningState.equals(ScanningState.SCANNING_ON)) {
                    byte[] buffer = new byte[17];
                    DatagramPacket packet = new DatagramPacket(buffer, 17);
                    con.receive(packet);

                    // on AP mode, WifiUtil.getIPAddress() returns null, fortunately the DHCP
                    // settings are hardcoded in Android and Cyanogenmod
                    // see https://github.com/CyanogenMod/android_frameworks_base/blob/cm-10.1/wifi/java/android/net/wifi/WifiStateMachine.java?source=c#L1299
                    // this should be replace by a method that grab the IP address instead of using hardcoded IP
                    if(WifiUtil.isWiFiApEnabled() && packet.getAddress().getHostAddress().equals("192.168.43.1"))
                        continue;

                    if(packet.getAddress().getHostAddress().equals(WifiUtil.getIPAddress()))
                        continue;

                    byte[] data = packet.getData();
                    if(data.length != 17)
                        continue;

                    ByteBuffer byteBuffer = ByteBuffer.wrap(data);

                    byte[] fingerprint_buf = new byte[16];
                    byteBuffer.get(fingerprint_buf, 0, 16);
                    String fingerprint = new String(fingerprint_buf);
                    if(!fingerprint.equals(RUMBLE_FINGERPRINT))
                        continue;

                    byte version = byteBuffer.get();
                    if(version > RUMBLE_VERSION)
                        continue;

                    lock.lock();
                    try {
                        if(wifiNeighborhood == null)
                            return;
                        WifiNeighbour neighbour = new WifiNeighbour(packet.getAddress().getHostAddress());
                        if (wifiNeighborhood.add(neighbour)) {
                            EventBus.getDefault().post(new ScannerNeighbourSensed(neighbour));
                        } else {
                            //Log.d(TAG, "beacon from " + neighbour.getLinkLayerAddress());
                            Runnable callback = timeouts.get(neighbour);
                            if (callback != null)
                                handler.removeCallbacks(callback);
                        }

                        Runnable callback = new RemoveNeighbour(neighbour);
                        timeouts.put(neighbour, callback);
                        handler.postDelayed(callback, NEIGHBOUR_TIMEOUT);
                    } finally {
                        lock.unlock();
                    }
                }
            } catch(UDPMulticastSocketException e) {
                stopScanner();
            } catch( IOException e) {
                stopScanner();
            }
        }
    });

    public class RemoveNeighbour implements Runnable {

        private WifiNeighbour neighbour;

        public RemoveNeighbour(WifiNeighbour neighbour) {
            this.neighbour = neighbour;
        }

        @Override
        public void run() {
            Log.d(TAG, "[-] neighbour "+neighbour.getLinkLayerAddress()+" timeouted");
            timeouts.remove(neighbour);
            lock.lock();
            try {
                if ((wifiNeighborhood != null) && wifiNeighborhood.remove(neighbour))
                    EventBus.getDefault().post(new ScannerNeighbourTimeout(neighbour));
            } finally {
                lock.unlock();
            }
        }

        @Override
        public boolean equals(Object o) {
            if(o == null)
                return false;
            if(o instanceof RemoveNeighbour) {
                RemoveNeighbour other = (RemoveNeighbour)o;
                if(this.neighbour.equals(other.neighbour))
                    return true;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return neighbour.hashCode();
        }

    }

    public void onEvent(ChannelDisconnected event) {
        if(!event.neighbour.getLinkLayerIdentifier().equals(WifiLinkLayerAdapter.LinkLayerIdentifier))
            return;
        Runnable callback = timeouts.get(event.neighbour);
        if(callback == null) {
            callback = new RemoveNeighbour((WifiNeighbour)event.neighbour);
            timeouts.put((WifiNeighbour)event.neighbour, callback);
            handler.postDelayed(callback, NEIGHBOUR_TIMEOUT);
        }
    }

}
