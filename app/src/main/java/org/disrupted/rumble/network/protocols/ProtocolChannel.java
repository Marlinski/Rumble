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

import android.os.HandlerThread;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.Worker;
import org.disrupted.rumble.network.protocols.command.Command;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import de.greenrobot.event.EventBus;


/**
 * The GenericProtocol implements a generic protocol where one thread takes care
 * of receiving and processing packet from the network while another one takes
 * care of receiving and processing command from the upper layer.
 * @author Marlinski
 */
public abstract class ProtocolChannel implements Worker {

    private static final String TAG = "ProtocolWorker";

    private BlockingQueue<Command> commandQueue;

    protected Protocol protocol;
    protected LinkLayerConnection con;
    protected Thread processingCommandFromQueue;

    protected boolean error;

    public ProtocolChannel(Protocol protocol, LinkLayerConnection con) {
        this.protocol = protocol;
        this.con = con;
        this.error = false;
        commandQueue = new LinkedBlockingQueue<Command>();
        this.processingCommandFromQueue = new Thread("CommandThread for "+con.getConnectionID()) {
            @Override
            public synchronized void run() {
                try {
                    while (true) {
                        Command command = commandQueue.take();
                        onCommandReceived(command);
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
     * - execute is public method to be called by upper layer
     */
    protected final void onChannelConnected() {
        processingCommandFromQueue.start();
        try {
            processingPacketFromNetwork();
        }finally {
            processingCommandFromQueue.interrupt();
        }
    }

    public final boolean execute(Command command){
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
