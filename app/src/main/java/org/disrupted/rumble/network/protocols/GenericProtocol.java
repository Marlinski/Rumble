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

import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.Connection;
import org.disrupted.rumble.network.protocols.command.Command;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Marlinski
 */
public abstract class GenericProtocol implements Connection, Protocol {


    private static final String TAG = "GenericProtocol";

    private BlockingQueue<Command> commandQueue;

    abstract protected void processingPacketFromNetwork() throws IOException;

    abstract protected boolean onCommandReceived(Command command);

    abstract protected void initializeProtocol();

    abstract protected void destroyProtocol();

    public GenericProtocol() {
        commandQueue = new LinkedBlockingQueue<Command>(1);
    }

    public final void onGenericProcotolConnected() throws IOException{

        /*
         * abstract method implemented by a specific protocol implementation to perform
         * some code when we the actual work of the protocol starts.
         */
        initializeProtocol();

        processingCommandFromQueue.start();

        try {
            processingPacketFromNetwork();
        }finally {
            processingCommandFromQueue.interrupt();
            destroyProtocol();
        }
    }

    Thread processingCommandFromQueue = new Thread() {
        @Override
        public synchronized void run() {
            try {
                while (true) {
                    Log.d(TAG, "[+] started command thread: "+getProtocolID());
                    Command command = commandQueue.take();
                    if(!isCommandSupported(command.getCommandName()))
                        continue;

                    onCommandReceived(command);
                }
            }
            catch(InterruptedException e) {
                Log.d(TAG, "[-] stopped command thread: "+getProtocolID());
                commandQueue.clear();
            }
        }
    };

    /*
     * Protocol Interface implementation of the executeCommand method
     * This method add a command to the commandQueue that will be processed by the
     * processingCommandFromQueue thread
     */
    public final boolean executeCommand(Command command) throws InterruptedException{
        if(isCommandSupported(command.getCommandName())) {
            commandQueue.put(command);
            return true;
        } else {
            Log.d(TAG, "[!] command is not supported: " + command.getCommandName());
            return false;
        }
    }
}
