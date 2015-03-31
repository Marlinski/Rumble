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
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.Scanner;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.LinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;
import org.disrupted.rumble.network.protocols.Protocol;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;
import org.disrupted.rumble.network.protocols.Worker;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;
import de.greenrobot.event.NoSubscriberEvent;


/**
 * NetworkCoordinator coordinate every network related events. It maintain an up-to-date
 * view of the neighbourhood history (past and present). It works closely with the NeighbourManager
 * which is responsible of a specific neighbour.
 *
 * This class is running in its own thread from boot-time of the application.
 *
 * @author Marlinski
 */
public class NetworkCoordinator extends Service {

    public static final String ACTION_START_FOREGROUND = "org.disruptedsystems.rumble.action.startforeground";
    public static final String ACTION_STOP_NETWORKING  = "org.disruptedsystems.rumble.action.stopnetworking";
    public static final String ACTION_MAIN_ACTION      = "org.disruptedsystems.rumble.action.mainaction";
    public static final int    FOREGROUND_SERVICE_ID   = 4242;

    private static final String TAG = "NetworkCoordinator";

    private static final Object lock = new Object();

    private Map<String, LinkLayerAdapter> adapters;
    private Map<String, WorkerPool> workerPools;
    private Map<String, Protocol> protocols;
    private List<Scanner> scannerList;

    private final IBinder mBinder = new LocalBinder();
    public class LocalBinder extends Binder {
        public NetworkCoordinator getService() {
            return NetworkCoordinator.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (lock) {
            Log.d(TAG, "[+] Starting NetworkCoordinator");

            scannerList = new LinkedList<Scanner>();

            // register link layers
            adapters = new HashMap<String, LinkLayerAdapter>();
            LinkLayerAdapter bluetoothAdapter = new BluetoothLinkLayerAdapter(this);
            LinkLayerAdapter wifiAdapter = new WifiManagedLinkLayerAdapter(this);
            adapters.put(bluetoothAdapter.getLinkLayerIdentifier(), bluetoothAdapter);
            adapters.put(wifiAdapter.getLinkLayerIdentifier(), wifiAdapter);

            // create worker pools
            workerPools = new HashMap<String, WorkerPool>();
            WorkerPool bluetoothWorkers = new WorkerPool(5);
            WorkerPool wifiManagedWorkers = new WorkerPool(5);
            workerPools.put(bluetoothAdapter.getLinkLayerIdentifier(), bluetoothWorkers);
            workerPools.put(wifiAdapter.getLinkLayerIdentifier(), wifiManagedWorkers);

            // register protocols
            protocols = new HashMap<String, Protocol>();
            Protocol rumbleProtocol = new RumbleProtocol(this);
            Protocol firechatProtocol = new FirechatProtocol(this);
            protocols.put(rumbleProtocol.getProtocolIdentifier(), rumbleProtocol);
            protocols.put(firechatProtocol.getProtocolIdentifier(), firechatProtocol);

            EventBus.getDefault().register(this);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        synchronized (lock) {
            Log.d(TAG, "[-] Destroy NetworkCoordinator");

            // destroy protocols
            for (Map.Entry<String, Protocol> entry : protocols.entrySet()) {
                entry.getValue().protocolStop();
                entry.setValue(null);
            }
            protocols.clear();
            protocols = null;

            // destroy worker pools
            for (Map.Entry<String, WorkerPool> entry : workerPools.entrySet()) {
                entry.getValue().stopPool();
                entry.setValue(null);
            }
            workerPools.clear();
            workerPools = null;

            // stop link layers
            for (Map.Entry<String, LinkLayerAdapter> entry : adapters.entrySet()) {
                entry.getValue().linkStop();
                entry.setValue(null);
            }
            adapters.clear();
            adapters = null;

            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (lock) {
            if (intent == null)
                return START_STICKY;

            if (intent.getAction().equals(ACTION_START_FOREGROUND)) {
                Log.d(TAG, "Received Start Foreground Intent ");

                for (Map.Entry<String, Protocol> entry : protocols.entrySet()) {
                    entry.getValue().protocolStart();
                }

                Intent notificationIntent = new Intent(this, RoutingActivity.class);
                notificationIntent.setAction(ACTION_MAIN_ACTION);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                        notificationIntent, 0);

                Notification notification = new NotificationCompat.Builder(this)
                        .setContentTitle("Rumble")
                        .setTicker("Rumble started")
                        .setContentText("Rumble started")
                        .setSmallIcon(R.drawable.small_icon)
                        .setContentIntent(pendingIntent)
                        .setOngoing(true).build();

                startForeground(FOREGROUND_SERVICE_ID, notification);

            }
        }

        return START_STICKY;
    }

    public void    startLinkLayer(String linkLayerIdentifier) {
        synchronized (lock) {
            if (adapters == null)
                return;
            if (workerPools == null)
                return;

            WorkerPool pool = workerPools.get(linkLayerIdentifier);
            if(pool == null)
                return;
            pool.startPool();

            LinkLayerAdapter adapter = adapters.get(linkLayerIdentifier);
            if (adapter == null)
                return;
            adapter.linkStart();
        }
    }
    public void    stopLinkLayer(String linkLayerIdentifier)  {
        synchronized (lock) {
            if (adapters == null)
                return;

            LinkLayerAdapter adapter = adapters.get(linkLayerIdentifier);
            if (adapter == null)
                return;
            adapter.linkStop();

            WorkerPool pool = workerPools.get(linkLayerIdentifier);
            if(pool == null)
                return;
            pool.stopPool();
        }
    }
    public boolean isLinkLayerEnabled(String linkLayerIdentifier) {
        synchronized (lock) {
            if (adapters == null)
                return false;
            LinkLayerAdapter linkLayer = adapters.get(linkLayerIdentifier);
            if (linkLayer == null)
                return false;
            else
                return linkLayer.isActivated();
        }
    }
    public boolean isScanning() {
        synchronized (lock) {
            for (Iterator<Scanner> it = scannerList.iterator(); it.hasNext(); ) {
                Scanner scanner = it.next();
                if(scanner.isScanning())
                    return true;
            }
            return false;
        }
    }
    public void    forceScan() {
        synchronized (lock) {
            for (Iterator<Scanner> it = scannerList.iterator(); it.hasNext(); ) {
                Scanner scanner = it.next();
                scanner.forceDiscovery();
            }
        }
    }

    public void addScanner(Scanner scanner) {
        for (Iterator<Scanner> it = scannerList.iterator(); it.hasNext(); ) {
            Scanner element = it.next();
            if(element == scanner)
                return;
        }
        scannerList.add(scanner);
    }

    public void delScanner(Scanner scanner) {
        for (Iterator<Scanner> it = scannerList.iterator(); it.hasNext(); ) {
            Scanner element = it.next();
            if(element == scanner) {
                it.remove();
                return;
            }
        }
    }

    public final List<NeighbourInfo>  getNeighborList() {
        List<NeighbourInfo> ret = new LinkedList<NeighbourInfo>();

        // first we search through every scanner we know
        for (Iterator<Scanner> it = scannerList.iterator(); it.hasNext(); ) {
            Scanner element = it.next();
            HashSet<LinkLayerNeighbour> set = element.getNeighbourList();
            for (LinkLayerNeighbour n : set) {
                ret.add(new NeighbourInfo(n));
            }
        }

        // then we ask the protocols
        // todo : merge
        for (Map.Entry<String, Protocol> entry : protocols.entrySet())
        {
            Protocol protocol = entry.getValue();
            List<ProtocolNeighbour> list = protocol.getNeighbourList();
            Iterator<ProtocolNeighbour> it = list.iterator();
            while(it.hasNext()) {
                ProtocolNeighbour protocolNeighbour = it.next();
                NeighbourInfo info = new NeighbourInfo(protocolNeighbour);
                ret.add(info);
            }
            list.clear();
        }
        return ret;
    }

    public boolean addWorker(Worker worker) {
        synchronized (lock) {
            if(workerPools == null)
                return false;
            String linkLayerIdentifier = worker.getLinkLayerIdentifier();
            if(workerPools.get(linkLayerIdentifier) == null)
                return false;
            workerPools.get(linkLayerIdentifier).addWorker(worker);
                return true;
        }
    }

    public final List<Worker> getWorkers(String linkLayerIdentifier, String protocolIdentifier, boolean active) {
        synchronized (lock) {
            if(workerPools == null)
                return null;

            WorkerPool pool = workerPools.get(linkLayerIdentifier);
            return pool.getWorkers(protocolIdentifier, active);
        }
    }

    public void stopWorkers(String linkLayerIdentifier, String protocolIdentifier) {
        synchronized (lock) {
            if(workerPools == null)
                return;

            WorkerPool pool = workerPools.get(linkLayerIdentifier);
            if(pool != null)
                pool.stopWorkers(protocolIdentifier);
        }
    }

    public void stopWorker(String linkLayerIdentifier, String workerID) {
        synchronized (lock) {
            if(workerPools == null)
                return;

            WorkerPool pool = workerPools.get(linkLayerIdentifier);
            pool.stopWorker(workerID);
        }
    }

    /*
     * Just to avoid warning in logcat
     */
    public void onEvent(NoSubscriberEvent event) {
    }
}
