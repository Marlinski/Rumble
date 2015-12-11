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

package org.disrupted.rumble.network.protocols.rumble.workers;

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.UDP.UDPMulticastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.events.CommandExecuted;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.events.ContactDisconnected;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashSet;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class RumbleOverUDPMulticast extends ProtocolChannel {

    private static final String TAG = "RumbleOverUDP";

    public static final int    PACKET_SIZE = 2048;

    private Set<Contact> recipientList;
    private boolean working;

    public RumbleOverUDPMulticast(RumbleProtocol protocol, UDPMulticastConnection con) {
        super(protocol, con);
        this.working = false;
        this.recipientList = new HashSet<Contact>();
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    public String getProtocolIdentifier() {
        return RumbleProtocol.protocolID;
    }

    @Override
    public String getWorkerIdentifier() {
        return getProtocolIdentifier()+" "+con.getConnectionID();
    }

    @Override
    public void cancelWorker() {
        return;
    }

    @Override
    public void startWorker() {
        if(working)
            return;
        working = true;
        EventBus.getDefault().register(this);

        try {
            con.connect();
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED: " + getWorkerIdentifier() + " " + exception.getMessage());
            return;
        }

        try {
            Log.d(TAG, "[+] CONNECTED: " + getWorkerIdentifier());
            onChannelConnected();

        } finally {
            stopWorker();
        }
    }

    @Override
    protected void processingPacketFromNetwork() {
        try {
            while(true) {
                byte[] buffer = new byte[PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket(buffer,  PACKET_SIZE);
                ((UDPMulticastConnection)con).receive(packet);
            }
        } catch (IOException e) {
        } catch (UDPMulticastSocketException e) {
        }
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        EventBus.getDefault().post(new CommandExecuted(this, command, false));
        return false;
    }

    @Override
    public void stopWorker() {
        if(!working)
            return;
        this.working = false;
        EventBus.getDefault().unregister(this);

        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
        }
        Log.d(TAG, "[-] ENDED: " + getWorkerIdentifier());
    }


    @Override
    public Set<Contact> getRecipientList() {
        return (Set)((HashSet)recipientList).clone();
    }
    public void onEvent(ContactInformationReceived event) {
        if(event.channel.equals(this))
            recipientList.add(event.contact);
    }
    public void onEvent(ContactDisconnected event) {
        if(recipientList.contains(event.contact))
            recipientList.remove(event.contact);
    }
}
