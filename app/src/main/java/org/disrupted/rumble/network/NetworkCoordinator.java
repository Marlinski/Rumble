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


import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import org.disrupted.rumble.R;
import org.disrupted.rumble.RoutingActivity;
import org.disrupted.rumble.network.events.NeighborhoodChanged;
import org.disrupted.rumble.network.events.NeighbourProtocolStart;
import org.disrupted.rumble.network.exceptions.ProtocolNotFoundException;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.exceptions.UnknownNeighbourException;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
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
 * NetworkCoordinator coordinate every network related events. It maintain an up-to-date
 * view of the neighbour history (past and present). It works closely with the NeighbourManager
 * which is responsible of a specific neighbour.
 *
 * This class is running in its own thread from boot-time of the application.
 *
 * @author Marlinski
 */
public class NetworkCoordinator extends Service {

    public static final String ACTION_START_FOREGROUND = "org.disruptedsystems.rumble.action.startforeground";
    public static final String ACTION_START_NETWORKING = "org.disruptedsystems.rumble.action.startnetworking";
    public static final String ACTION_STOP_NETWORKING  = "org.disruptedsystems.rumble.action.stopnetworking";
    public static final String ACTION_MAIN_ACTION      = "org.disruptedsystems.rumble.action.mainaction";
    public static final int    FOREGROUND_SERVICE_ID   = 4242;

    private static final String TAG = "NetworkCoordinator";

    private static NetworkCoordinator instance;
    private static final Object lock = new Object();

    private HashSet<NeighbourManager> neighborhoodHistory;
    private Map<String, LinkLayerAdapter> adapters;

    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        public NetworkCoordinator getService() {
            return NetworkCoordinator.this;
        }
    }

    // little hack to avoid binding to NetworkCoordinator which seems too bulky for certain usage
    // this must only be called by the networking thread and classes it is safe because
    // if NetworkCoordinator is destroy, so are all the related networking thread and classes
    // todo: hmm maybe think of a better design ?
    public static NetworkCoordinator getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "[+] Starting NetworkCoordinator");
        neighborhoodHistory = new HashSet<NeighbourManager>();
        adapters = new HashMap<String, LinkLayerAdapter>();
        LinkLayerAdapter bluetoothAdapter = new BluetoothLinkLayerAdapter(this);
        LinkLayerAdapter wifiAdapter = new WifiManagedLinkLayerAdapter(this);
        adapters.put(bluetoothAdapter.getLinkLayerIdentifier(), bluetoothAdapter);
        adapters.put(wifiAdapter.getLinkLayerIdentifier(), wifiAdapter);
        instance = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "[-] Destroy NetworkCoordinator");
        synchronized (lock) {
            neighborhoodHistory.clear();
            for (Map.Entry<String, LinkLayerAdapter> entry : adapters.entrySet()) {
                entry.getValue().linkStop();
            }
            adapters.clear();
        }
        instance = null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals(ACTION_START_FOREGROUND)) {
            Log.d(TAG, "Received Start Foreground Intent ");

            Intent notificationIntent = new Intent(this, RoutingActivity.class);
            notificationIntent.setAction(ACTION_MAIN_ACTION);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);

            /*
            Intent startNetwork = new Intent(this, NetworkCoordinator.class);
            startNetwork.setAction(ACTION_START_NETWORKING);
            PendingIntent pstartNetwork = PendingIntent.getService(this, 0, startNetwork, 0);
            */

            Notification notification = new NotificationCompat.Builder(this)
                    .setContentTitle("Rumble")
                    .setTicker("Rumble started")
                    .setContentText("Rumble started")
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true).build();
                    //.addAction(android.R.drawable.ic_media_play,
                    //        "Start", pstartNetwork).build();

            startForeground(FOREGROUND_SERVICE_ID, notification);

        } else if (intent.getAction().equals(ACTION_START_NETWORKING)) {
            Log.i(TAG, "Clicked Start");
        } else if (intent.getAction().equals(ACTION_STOP_NETWORKING)) {
            Log.i(TAG, "Clicked Stop");
        }

        return START_STICKY;
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
    private NeighbourManager getNeighbourRecordFromDeviceAddress(String address) {
        synchronized (lock) {
            Iterator<NeighbourManager> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourManager record = it.next();
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
    public List<LinkLayerNeighbour>  getNeighborList() {
        List<LinkLayerNeighbour> neighborhoodList = new LinkedList<LinkLayerNeighbour>();
        synchronized (lock) {
            Iterator<NeighbourManager> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourManager record = it.next();
                if (record.isInRange()) {
                    List<LinkLayerNeighbour> list = record.getPresences();
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
    public boolean isNeighbourConnectedLinkLayer(LinkLayerNeighbour neighbour, String linkLayerIdentifier) throws RecordNotFoundException{
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(neighbour.getLinkLayerAddress());
        if(record == null)
            throw new RecordNotFoundException();
        return record.isConnectedLinkLayer(linkLayerIdentifier);
    }

    /*
     * isNeighbourConnectedWithProtocol return true if we are connected to the neighbour
     * using the protocolID
     * it throws the RecordNotFoundException if the record has not been found
     */
    public boolean isNeighbourConnectedWithProtocol(LinkLayerNeighbour neighbour, String protocolID) throws RecordNotFoundException{
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(neighbour.getLinkLayerAddress());
        if(record == null)
            throw new RecordNotFoundException();
        return record.isConnectedWithProtocol(protocolID);
    }

    /*
     * isNeighbourInRange return true if the neighbour is in range false otherwise
     * it throws the RecordNotFoundException if the record has not been found
     */
    public boolean isNeighbourInRange(LinkLayerNeighbour neighbour, String linkLayerIdentifier) throws RecordNotFoundException {
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(neighbour.getLinkLayerAddress());
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
    public String getNeighbourName(LinkLayerNeighbour neighbour) throws RecordNotFoundException{
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(neighbour.getLinkLayerAddress());
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
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(address);
        if(record == null)
            throw new RecordNotFoundException();
        boolean bool = record.addProtocol(protocol);
        if(bool) {
            //todo be more neighbour specific
            EventBus.getDefault().post(new NeighborhoodChanged());
            EventBus.getDefault().post(new NeighbourProtocolStart(address, protocol.getProtocolID()));
        }
        return bool;
    }
    /*
     * delProtocol removes a protocol instance from a NeighbourRecord based on his macAddress of
     * one of its presence.
     * it returns true if the record has been found and protocol has been removed
     * it returns false if the record has been found but the protocol has not been removed
     * it throws an exception if the record has not been found
     */
    public boolean delProtocol(String address, Protocol protocol) throws RecordNotFoundException, ProtocolNotFoundException {
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(address);
        if(record == null)
            throw new RecordNotFoundException();
        boolean bool = record.delProtocol(protocol);
        if(bool) {
            //todo be more neighbour specific
            EventBus.getDefault().post(new NeighborhoodChanged());
            EventBus.getDefault().post(new NeighbourProtocolStart(address, protocol.getProtocolID()));
        }
        return bool;
    }

    /*
     * newNeighbor is called whenever a new Neighbour is found by one of the linkLayerScanner
     * If the neighbour is discovered for the first time, a NeighbourRecord is created
     * Anyway, it will try to connect to it with every protocol available (see connector).
     */
    public void newNeighbour(LinkLayerNeighbour newNeighbour) {
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(newNeighbour.getLinkLayerAddress());
        synchronized (lock) {
            if (record == null) {
                Log.d(TAG, "[+] new neighbour record");
                record = new NeighbourManager(newNeighbour);
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
     * It throws a RecordNotFoundException if the neighbour was not found
     */
    public boolean delNeighbor(String address) throws RecordNotFoundException {
        NeighbourManager record = getNeighbourRecordFromDeviceAddress(address);
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
     * The eventual associated protocols will normally be removed by themselves when the socket
     * close but the neighbour view must be removed manually
     * todo maybe think of something more graceful
     */
    public void removeNeighborsType(String linkLayerIdentifier) {
        synchronized (lock) {
            Iterator<NeighbourManager> it = neighborhoodHistory.iterator();
            while (it.hasNext()) {
                NeighbourManager record = it.next();
                record.delDeviceType(linkLayerIdentifier);
            }
            EventBus.getDefault().post(new NeighborhoodChanged());
        }
    }
}
