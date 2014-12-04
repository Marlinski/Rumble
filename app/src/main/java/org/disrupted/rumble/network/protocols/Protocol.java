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

package org.disrupted.rumble.network.protocols;

import android.util.Log;

import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.message.Message;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.NeighbourDevice;
import org.disrupted.rumble.network.NeighbourRecord;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.protocols.command.ProtocolCommand;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The Protocol abstract class is a generic implementation of a message-based protocol.
 * When connection is established by the LinkLayer (for instance BluetoothConnection),
 * it calls protocol.onConnected() which in turns calls the protocol specific method
 * to be implemented initializeConneciton()
 *
 * onConnected then listens for incomming packet (processingPacketFromNetwork) from the network
 * and also creates another thread for processing incoming command (processingCommandFromQueue)
 * that are sent from upper layer (Routing or UI).
 *
 *    - abstract method processingPacketFromNetwork should be an infinite loop.
 *      When it returns connection stops
 *    - When a command is received, abstract method onCommandReceived is called
 *
 * The Protocol Implemetation have access to the InputStream and OutputStream
 *
 * @author Marlinski
 */
public abstract class Protocol {

    private static final String TAG = "Protocol";

    protected String protocolID = "Protocol";

    protected BlockingQueue<ProtocolCommand> commandQueue;
    private boolean running = false;
    protected Thread queueThread = null;
    protected String macAddress;
    protected InputStream in;
    protected OutputStream out;
    protected NeighbourRecord record;

    public String getProtocolID() {
        return protocolID;
    }

    abstract public Protocol newInstance();

    abstract public boolean isCommandSupported(String commandName);

    abstract public void initializeConnection();

    abstract public void processingPacketFromNetwork() throws IOException;

    abstract public boolean onCommandReceived(ProtocolCommand command);

    abstract public void destroyConnection();

    public boolean isRunning() {
        return running;
    }

    public final void onConnected(String macAddress, InputStream in, OutputStream out) {
        this.macAddress = macAddress;
        record = NetworkCoordinator.getInstance().getNeighbourRecordFromDeviceAddress(macAddress);
        if(record == null) {
            Log.e(TAG, "[!] cannot get the record of user: "+macAddress);
        }
        commandQueue = new LinkedBlockingQueue<ProtocolCommand>();
        this.in = in;
        this.out = out;
        running = true;

        initializeConnection();

        queueThread = new Thread() {
            @Override
            public synchronized void run(){
                processingCommandFromQueue();
            }
        };
        queueThread.start();

        try {  processingPacketFromNetwork();  }  catch(IOException ignore) { }

        queueThread.interrupt();
        destroyConnection();
    }

    public void processingCommandFromQueue(){
        try {
            while (true) {
                ProtocolCommand command = commandQueue.take();
                if(!isCommandSupported(command.getCommandName())){
                    continue;
                }
                onCommandReceived(command);
            }
        }
        catch(InterruptedException e) {
            commandQueue.clear();
        }
    }

    public void stop() {
        // normally the socket.close(); raise exception
        // and thus kills all the threads nicely
    }

    /*
     * "executeCommand" is called by the upper layers (Routing, UI)
     */
    public final boolean executeCommand(ProtocolCommand command) {
        if(isCommandSupported(command.getCommandName())) {
            commandQueue.add(command);
            return true;
        } else {
            Log.d(TAG, "[!] command is not supported: "+command.getCommandName());
            return false;
        }
    }

    /*
     * The following list of method are called by the Protocol implementations
     */
    public final void onStatusMessageReceived(StatusMessage message) {
        Log.d(TAG, "Status received from Network:\n" + message.toString());
        DatabaseFactory
                .getStatusDatabase(NetworkCoordinator.getInstance())
                .insertStatus(message,null);
    }

}
