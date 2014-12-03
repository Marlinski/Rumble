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
 * it calls protocol.onConnected()
 *
 * onConnected listens for incomming packet (processingPacketFromNetwork) from the network
 * and creates another thread for processing incoming command (processingCommandFromQueue) that
 * are sent from upper layer (Routing, UI).
 *
 *    - When a packet is received, abstract method onPacketReceived is called
 *    - When a command is received, abstract method onCommandReceived is called
 *
 * Any Protocol Implemetation have access to the InputStream and OutputStream for any protocol
 * specific interaction.
 *
 * @author Marlinski
 */
public abstract class Protocol {

    private static final String TAG = "Protocol";

    protected String protocolID = "Protocol";

    protected BlockingQueue<ProtocolCommand> commandQueue;
    private boolean running = false;
    protected Thread queueThread = null;
    protected InputStream in;
    protected OutputStream out;

    public String getProtocolID() {
        return protocolID;
    }

    abstract public Protocol newInstance();

    abstract public boolean isCommandSupported(String commandName);

    abstract public boolean onPacketReceived(byte[] bytes, int size);

    abstract public boolean onCommandReceived(ProtocolCommand command);

    public boolean isRunning() {
        return running;
    }

    public void onConnected(InputStream in, OutputStream out) {
        commandQueue = new LinkedBlockingQueue<ProtocolCommand>();
        this.in = in;
        this.out = out;
        running = true;

        queueThread = new Thread() {
            @Override
            public synchronized void run(){
                processingCommandFromQueue();
            }
        };
        queueThread.start();

        processingPacketFromNetwork();
    }

    public void processingPacketFromNetwork(){
        try {
            while (true) {
                byte[] bytes = new byte[1024];
                int size = in.read(bytes);
                if(size > 0)
                    onPacketReceived(bytes, size);
            }
        }
        catch(IOException e){
            queueThread.interrupt();
        }
    }

    public void processingCommandFromQueue(){
        try {
            while (true) {
                ProtocolCommand command = commandQueue.take();
                if(!isCommandSupported(command.getCommandName())){
                    continue;
                }
                Log.d(TAG, "[+] Command received " + command.getCommandName());
            }
        }
        catch(InterruptedException e) {
            commandQueue.clear();
        }
    }



    public void stop() {
        if(!running)
            return;
    }

    /*
     * "executeCommand" is called by the upper layers (Routing, UI)
     */
    public boolean executeCommand(ProtocolCommand command) {
        if(isCommandSupported(command.getCommandName())) {
            commandQueue.add(command);
            return true;
        } else {
            return false;
        }
    }

    /*
     * The following list of method are called by the Protocol implementations
     */
    public void onStatusMessageReceived(StatusMessage message) {
        Log.d(TAG, "Status received:\n" + message.toString());
        DatabaseFactory
                .getStatusDatabase(NetworkCoordinator.getInstance())
                .insertStatus(message,null);
    }

}
