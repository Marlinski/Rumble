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

package org.disrupted.rumble.network;


import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.disrupted.rumble.network.events.NeighborhoodChanged;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;
import org.disrupted.rumble.network.protocols.Protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class NetworkCoordinator extends Service {

    private static final String TAG = "NetworkCoordinator";

    private static NetworkCoordinator instance;
    private final IBinder mBinder = new LocalBinder();
    private static final Object lock = new Object();

    /*
     * ACTION order it receives through Intent
     */
    public static final String ACTION_FORCE_START_BLUETOOTH = "service.action.START_BLUETOOTH";
    public static final String ACTION_FORCE_STOP_BLUETOOTH  = "service.action.STOP_BLUETOOTH";
    public static final String ACTION_FORCE_START_WIFI      = "service.action.START_WIFI";
    public static final String ACTION_FORCE_STOP_WIFI       = "service.action.STOP_WIFI";

    private HashSet<NeighbourRecord> neighborhoodHistory;
    private Map<String, LinkLayerAdapter> adapters;

    public static NetworkCoordinator getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (lock) {
            neighborhoodHistory = new HashSet<NeighbourRecord>();
            adapters = new HashMap<String, LinkLayerAdapter>();
            instance = this;
            LinkLayerAdapter bluetoothAdapter = new BluetoothLinkLayerAdapter(this);
            LinkLayerAdapter wifiAdapter = new WifiManagedLinkLayerAdapter(this);
            adapters.put(bluetoothAdapter.getID(), bluetoothAdapter);
            adapters.put(wifiAdapter.getID(), wifiAdapter);
        }
    }

    @Override
    public void onDestroy() {
        synchronized (lock) {
            neighborhoodHistory.clear();
            for (Map.Entry<String, LinkLayerAdapter> entry : adapters.entrySet()) {
                entry.getValue().linkStop();
            }
            instance = null;
            adapters.clear();
            super.onDestroy();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public NetworkCoordinator getService() {
            return NetworkCoordinator.this;
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent == null) return START_STICKY;
        if(intent.getAction().equals(ACTION_FORCE_START_BLUETOOTH)) {
            Log.d(TAG, "[+] Receive START Bluetooth Intent");
            adapters.get(BluetoothLinkLayerAdapter.LinkLayerIdentifier).linkStart();
        }
        if(intent.getAction().equals(ACTION_FORCE_STOP_BLUETOOTH)) {
            Log.d(TAG, "[-] Receive STOP Bluetooth Intent");
            adapters.get(BluetoothLinkLayerAdapter.LinkLayerIdentifier).linkStop();
        }
        if(intent.getAction().equals(ACTION_FORCE_START_WIFI)) {
            Log.d(TAG, "[+] Receive START Wifi Intent");
            adapters.get(WifiManagedLinkLayerAdapter.LinkLayerIdentifier).linkStart();
        }
        if(intent.getAction().equals(ACTION_FORCE_STOP_WIFI)) {
            Log.d(TAG, "[-] Receive STOP Wifi Intent");
            adapters.get(WifiManagedLinkLayerAdapter.LinkLayerIdentifier).linkStop();
        }
        return START_STICKY;
    }

    public boolean isLinkLayerEnabled(String linkLayerIdentifier) {
        LinkLayerAdapter linkLayer = adapters.get(linkLayerIdentifier);
        if(linkLayer == null)
            return false;
        else
            return linkLayer.isActivated();
    }

    public boolean isScanning() {
        Iterator<Map.Entry<String, LinkLayerAdapter>> it = adapters.entrySet().iterator();
        while(it.hasNext()) {
            LinkLayerAdapter adapter = it.next().getValue();
            if(adapter.isScanning())
                return true;
        }
        return false;
    }
    public void forceScan() {
        Iterator<Map.Entry<String, LinkLayerAdapter>> it = adapters.entrySet().iterator();
        while(it.hasNext()) {
            LinkLayerAdapter adapter = it.next().getValue();
            if(adapter.isActivated())
                adapter.forceDiscovery();
        }
    }



    public List<String> getNeighborList() {
        List<String> neighborhoodList = new LinkedList<String>();
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord element = it.next();
                if (element.isInRange())
                    neighborhoodList.add(element.getID());
            }
        }
        return neighborhoodList;
    }

    public NeighbourRecord getNeighbourRecordFromID(String id) {
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord element = it.next();
                if (element.getID().equals(id))
                    return element;
            }
            return null;
        }
    }
    public NeighbourRecord getNeighbourRecordFromDeviceAddress(String address) {
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord element = it.next();
                if (element.isDevice(address))
                    return element;
            }
            return null;
        }
    }

    public void addProtocol(String address, Protocol protocol) {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(address);
        if(record == null) {
            //INFO that should never happen
            return;
        }
        NeighbourDevice device = record.getDeviceFromAddress(address);
        synchronized (lock) {
            device.addProtocol(protocol);
        }
        //todo: only post neighbour event
        EventBus.getDefault().post(new NeighborhoodChanged());
    }
    public void delProtocol(String address, String protocolID) {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(address);
        if(record == null) {
            //INFO that should only happen for server
            return;
        }
        NeighbourDevice device = record.getDeviceFromAddress(address);
        synchronized (lock) {
            device.delProtocol(protocolID);
        }
        //todo: only post neighbour event
        EventBus.getDefault().post(new NeighborhoodChanged());
    }


    public void newNeighbor(NeighbourDevice newNeighbourDevice) {
        NeighbourRecord neighbourRecord = getNeighbourRecordFromDeviceAddress(newNeighbourDevice.getMacAddress());
        synchronized (lock) {
            if (neighbourRecord == null) {
                neighbourRecord = new NeighbourRecord(newNeighbourDevice);
                neighborhoodHistory.add(neighbourRecord);
            }
            EventBus.getDefault().post(new NeighborhoodChanged());
        }
        this.connector(neighbourRecord);
    }
    public void delNeighbor(String address) {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(address);
        if (record == null){
            Log.d(TAG, "[!] cannot update a non-existing record");
            return;
        }
        synchronized (lock) {
            Log.d(TAG, "[+] neighbour disappeared: " + address);
            record.delDevice(address);
            EventBus.getDefault().post(new NeighborhoodChanged());
        }
    }
    public void removeNeighborsType(String linkLayerIdentifier) {
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord element = it.next();
                element.delDeviceType(linkLayerIdentifier);
            }
            EventBus.getDefault().post(new NeighborhoodChanged());
        }
    }

    /*
     * Interface Selection
     * It is very possible to be connected to the same neighbour from different adapter at
     * the same time (for instance with Bluetooth and Wifi).
     * In that case, and in order to optimize the resource, connector will find the best
     * linklayer adapter and try to connect to it from it.
     * todo: the scoring algorithm.
     */
    private void connector(NeighbourRecord record) {
        NeighbourDevice neighbourDevice = record.getBestDevice();
        LinkLayerAdapter adapter = adapters.get(neighbourDevice.getType());
        adapter.connectTo(neighbourDevice, true);
    }

}
