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


import android.util.Log;

import org.disrupted.rumble.events.NeighborhoodChanged;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.exceptions.UnknownNeighbourException;
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
public class NetworkCoordinator {

    private static final String TAG = "NetworkCoordinator";

    private static NetworkCoordinator instance;
    private static final Object lock = new Object();


    private HashSet<NeighbourRecord> neighborhoodHistory;
    private Map<String, LinkLayerAdapter> adapters;

    public static NetworkCoordinator getInstance() {
        synchronized (lock) {
            if (instance == null)
                instance = new NetworkCoordinator();

            return instance;
        }
    }

    private NetworkCoordinator() {
        Log.d(TAG, "[+] Starting NetworkCoordinator");
        neighborhoodHistory = new HashSet<NeighbourRecord>();
        adapters = new HashMap<String, LinkLayerAdapter>();
        LinkLayerAdapter bluetoothAdapter = new BluetoothLinkLayerAdapter(this);
        LinkLayerAdapter wifiAdapter = new WifiManagedLinkLayerAdapter(this);
        adapters.put(bluetoothAdapter.getID(), bluetoothAdapter);
        adapters.put(wifiAdapter.getID(), wifiAdapter);
    }

    public void clean() {
        Log.d(TAG, "[-] Destroy NetworkCoordinator");
        synchronized (lock) {
            neighborhoodHistory.clear();
            for (Map.Entry<String, LinkLayerAdapter> entry : adapters.entrySet()) {
                entry.getValue().linkStop();
            }
            instance = null;
            adapters.clear();
        }
    }


    public void    startBluetooth() {
        adapters.get(BluetoothLinkLayerAdapter.LinkLayerIdentifier).linkStart();
    }
    public void    stopBluetooth()  {
        adapters.get(BluetoothLinkLayerAdapter.LinkLayerIdentifier).linkStop();
    }
    public void    startWifi()      {
        adapters.get(WifiManagedLinkLayerAdapter.LinkLayerIdentifier).linkStart();
    }
    public void    stopWifi()       {
        adapters.get(WifiManagedLinkLayerAdapter.LinkLayerIdentifier).linkStop();
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
    public void    forceScan() {
        Iterator<Map.Entry<String, LinkLayerAdapter>> it = adapters.entrySet().iterator();
        while(it.hasNext()) {
            LinkLayerAdapter adapter = it.next().getValue();
            if(adapter.isActivated())
                adapter.forceDiscovery();
        }
    }

    /*
     * getNeighbourRecordFromDeviceAddress returns the local record from a specific Neighbour
     * macAddress. It is a private utility function for NetworkCoordinator
     */
    private NeighbourRecord getNeighbourRecordFromDeviceAddress(String address) {
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord record = it.next();
                if (record.is(address))
                    return record;
            }
            return null;
        }
    }


    /*
     * getNeighborList returns the list of neighbour for every activated interface
     * and every activated protocol.
     */
    public List<Neighbour>  getNeighborList() {
        List<Neighbour> neighborhoodList = new LinkedList<Neighbour>();
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord record = it.next();
                if (record.isInRange()) {
                    List<Neighbour> list = record.getPresences();
                    if(list != null)
                        neighborhoodList.addAll(list);
                }
            }
        }
        return neighborhoodList;
    }

    /*
     * isNeighbourConnected returns true if we are connected to the neighbour with any protocol
     * it throws the RecordNotFoundException if the record has not been found
     */
    public boolean isNeighbourConnectedLinkLayer(Neighbour neighbour, String linkLayerIdentifier) throws RecordNotFoundException{
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(neighbour.getMacAddress());
        if(record == null)
            throw new RecordNotFoundException();
        return record.isConnectedLinkLayer(linkLayerIdentifier);
    }

    /*
     * isNeighbourConnectedWithProtocol return true if we are connected to the neighbour
     * using the protocolID
     * it throws the RecordNotFoundException if the record has not been found
     */
    public boolean isNeighbourConnectedWithProtocol(Neighbour neighbour, String protocolID) throws RecordNotFoundException{
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(neighbour.getMacAddress());
        if(record == null)
            throw new RecordNotFoundException();
        return record.isConnectedWithProtocol(protocolID);
    }

    /*
     * isNeighbourInRange return true if the neighbour is in range false otherwise
     * it throws the RecordNotFoundException if the record has not been found
     */
    public boolean isNeighbourInRange(Neighbour neighbour, String linkLayerIdentifier) throws RecordNotFoundException {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(neighbour.getMacAddress());
        if(record == null)
            throw new RecordNotFoundException();
        try {
            return record.isInRange(linkLayerIdentifier);
        } catch (UnknownNeighbourException impossibleCauseRecordFound) { }
        return false;
    }

    /*
     * getNeighbourName returns the name of the neighbour.
     * note: it may be undefinied if no message has been exchanged
     */
    public String getNeighbourName(Neighbour neighbour) throws RecordNotFoundException{
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(neighbour.getMacAddress());
        if(record == null)
            throw new RecordNotFoundException();
        return record.getName();
    }

    /*
     * addProtocol adds a protocol instance to a NeighbourRecord from a macAddress of one of
     * its presence.
     * it returns true if the record has been found and protocol has been added
     * it returns false if the record has been found but the protocol has not been added
     * it throws an exception if the record has not been found
     */
    public boolean addProtocol(String address, Protocol protocol) throws RecordNotFoundException {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(address);
        if(record == null)
            throw new RecordNotFoundException();
        return record.addProtocol(protocol);
    }
    /*
     * delProtocol removes a protocol instance from a NeighbourRecord based on his macAddress of
     * one of its presence.
     * it returns true if the record has been found and protocol has been removed
     * it returns false if the record has been found but the protocol has not been removed
     * it throws an exception if the record has not been found
     */
    public boolean delProtocol(String address, Protocol protocol) throws RecordNotFoundException {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(address);
        if(record == null)
            throw new RecordNotFoundException();
        return record.delProtocol(protocol);
    }

    /*
     * newNeighbor is called whenever a new Neighbour is found by one of the linkLayerScanner
     * If the neighbour is discovered for the first time, a NeighbourRecord is created
     * Anyway, it will try to connect to it with every protocol available (see connector).
     */
    public void newNeighbour(Neighbour newNeighbour) {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(newNeighbour.getMacAddress());
        synchronized (lock) {
            if (record == null) {
                Log.d(TAG, "[+] new neighbour record");
                record = new NeighbourRecord(newNeighbour);
                neighborhoodHistory.add(record);
                EventBus.getDefault().post(new NeighborhoodChanged());
            } else {
                if(record.addPresence(newNeighbour))
                    EventBus.getDefault().post(new NeighborhoodChanged());
            }
        }
        /*
         * whatever happens, we try to connect, the link layer specific connectTo will
         * decide what to do
         */
        LinkLayerAdapter adapter = adapters.get(newNeighbour.getLinkLayerType());
        adapter.connectTo(newNeighbour, true);
    }

    /*
     * delNeighbour is called whenever the linkLayerScanner believes this neighbour has
     * disappeared.
     * It returns true if we successfully removed the neighbour
     * Sometime the linklayerscanner can be wrong (it misses some neighbours) and so we
     * return false If we are still actively connected to the neighbour (with any protocol)
     * It throws a RecordNotFoundException if the record was not found
     */
    public boolean delNeighbor(String address) throws RecordNotFoundException {
        NeighbourRecord record = getNeighbourRecordFromDeviceAddress(address);
        if (record == null)
            throw new RecordNotFoundException();
        synchronized (lock) {
            try {
                if (record.delPresence(address)) {
                    Log.d(TAG, "[+] neighbour disappeared: " + address);
                    EventBus.getDefault().post(new NeighborhoodChanged());
                    return true;
                }
            } catch(UnknownNeighbourException impossibleCauseRecordFound) {}
        }
        return false;
    }

    /*
     * This function is called when the user shut down an interface
     * In that case we must remove every entry related to a specific LinkLayerIdentifier
     * The eventual associated protocols will normally be removed by themselves but the neighbours
     * must be removed manually
     * todo maybe think of something more gracefull
     */
    public void removeNeighborsType(String linkLayerIdentifier) {
        synchronized (lock) {
            Iterator<NeighbourRecord> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourRecord record = it.next();
                record.delDeviceType(linkLayerIdentifier);
            }
            EventBus.getDefault().post(new NeighborhoodChanged());
        }
    }

}
