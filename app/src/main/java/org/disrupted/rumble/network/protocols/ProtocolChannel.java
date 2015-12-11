/*
 * Copyright (C) 2014 Lucien Loiseau
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

import android.os.HandlerThread;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.Worker;
import org.disrupted.rumble.network.protocols.command.Command;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;


/**
 * The GenericProtocol implements a generic protocol where one thread takes care
 * of receiving and processing packet from the network while another one takes
 * care of receiving and processing command from the upper layer.
 * @author Lucien Loiseau
 */
public abstract class ProtocolChannel implements Worker {

    private static final String TAG = "ProtocolWorker";

    private BlockingQueue<Command> commandQueue;
    private final ReentrantLock lock = new ReentrantLock();

    protected Protocol protocol;
    protected LinkLayerConnection con;
    protected Thread processingCommandFromQueue;
    protected boolean error;

    // statistics
    public long connection_start_time;
    public long connection_end_time;
    public long bytes_received;
    public long in_transmission_time;
    public long bytes_sent;
    public long out_transmission_time;
    public int  status_sent;
    public int  status_received;

    public ProtocolChannel(Protocol protocol, LinkLayerConnection con) {
        this.protocol = protocol;
        this.con = con;
        this.error = false;

        // initialising statistics
        this.connection_start_time = System.nanoTime();
        this.connection_end_time = System.nanoTime();
        this.bytes_received = 0;
        this.bytes_sent = 0;
        this.status_received = 0;
        this.status_sent = 0;
        this.in_transmission_time = 0;
        this.out_transmission_time = 0;

        // starting network receiving thread + command thread
        commandQueue = new LinkedBlockingQueue<Command>();
        this.processingCommandFromQueue = new Thread("CommandThread for "+con.getConnectionID()) {
            @Override
            public synchronized void run() {
                try {
                    while (true) {
                        Command command = commandQueue.take();
                        try {
                            lock.lock();
                            onCommandReceived(command);
                        } finally {
                            lock.unlock();
                        }
                    }
                }
                catch(InterruptedException e) {
                    commandQueue.clear();
                }
            }
        };
    }

    public LinkLayerConnection getLinkLayerConnection() {
        return con;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public String getProtocolIdentifier() {
        return protocol.getProtocolIdentifier();
    }

    @Override
    public String getWorkerIdentifier() {
        return getProtocolIdentifier()+" "+con.getConnectionID();
    }

    /*
     * to be implemented
     * - onCommandReceived when a message is received from upper layer (Service Data Unit)
     * - processingPacketFromNetwork when a message is received from lower layer (network)
     */
    abstract protected boolean onCommandReceived(Command command);

    abstract protected void processingPacketFromNetwork();

    abstract public Set<Contact> getRecipientList();

    /*
     * class API
     * - onChannelConnected must be called by implementing class to start the receiving thread
     * - execute and executeNonBlocking is public method to be called by upper layer
     */
    protected final void onChannelConnected() {
        connection_start_time = System.nanoTime();
        processingCommandFromQueue.start();
        try {
            processingPacketFromNetwork();
        }finally {
            processingCommandFromQueue.interrupt();
            connection_end_time = System.nanoTime();
        }
    }

    public final boolean execute(Command command){
        lock.lock();
        try {
            return onCommandReceived(command);
        } finally {
            lock.unlock();
        }
    }

    public final boolean executeNonBlocking(Command command){
        try {
            commandQueue.put(command);
            return true;
        } catch (InterruptedException ignore) {
            return false;
        }
    }

    public int getChannelPriority() {
        return this.getLinkLayerConnection().getLinkLayerPriority() +
               this.protocol.getProtocolPriority();
    }

    /*
     * for easy use in Set, List, Map...
     */
    @Override
    public boolean equals(Object o) {
        if(o == null)
            return false;
        if(o instanceof ProtocolChannel) {
            ProtocolChannel channel = (ProtocolChannel)o;
            return this.getWorkerIdentifier().equals(channel.getWorkerIdentifier());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return this.getWorkerIdentifier().hashCode();
    }

    @Override
    public String toString() {
        return this.getWorkerIdentifier();
    }
}
