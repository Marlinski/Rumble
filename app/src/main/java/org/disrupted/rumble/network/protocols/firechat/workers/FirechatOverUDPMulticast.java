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

package org.disrupted.rumble.network.protocols.firechat.workers;

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.linklayer.exception.UDPMulticastSocketException;
import org.disrupted.rumble.network.linklayer.wifi.UDP.UDPMulticastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.CommandSendChatMessage;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.network.protocols.events.CommandExecuted;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.firechat.FirechatMessageParser;
import org.disrupted.rumble.network.protocols.firechat.FirechatProtocol;
import org.disrupted.rumble.network.events.ContactDisconnected;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashSet;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class FirechatOverUDPMulticast extends ProtocolChannel {


    private static final String TAG = "FirechatOverUDP";

    public static final String MULTICAST_ADDRESS  = "239.192.0.0";
    public static final int    MULTICAST_UDP_PORT = 7576;
    public static final int    PACKET_SIZE = 2048;

    private DatagramPacket         packet;
    private boolean                working;
    private Set<Contact>           recipientList;

    private static final FirechatMessageParser parser = new FirechatMessageParser();

    public FirechatOverUDPMulticast(FirechatProtocol protocol, UDPMulticastConnection con) {
        super(protocol, con);
        byte[] buffer = new byte[PACKET_SIZE];
        this.packet = new DatagramPacket(buffer,  PACKET_SIZE);
        working = false;
        this.recipientList = new HashSet<Contact>();
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public String getProtocolIdentifier() {
        return FirechatProtocol.protocolID;
    }

    @Override
    public LinkLayerConnection getLinkLayerConnection() {
        return con;
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
            /* todo how to we deal with multicast channel ?
            EventBus.getDefault().post(new NeighbourConnected(
                            con.getLinkLayerNeighbour(),
                            this)
            );
            */
            onChannelConnected();

        } finally {
            /*
            EventBus.getDefault().post(new NeighbourDisconnected(
                            con.getLinkLayerNeighbour(),
                            this)
            );
            */
            stopWorker();
        }
    }

    @Override
    protected void processingPacketFromNetwork() {
        try {
            while(true) {
                ((UDPMulticastConnection)con).receive(packet);
                ChatMessage chatMessage;
                try {
                    String jsonString = new String(packet.getData(), 0, packet.getLength());
                    JSONObject message = new JSONObject(jsonString);
                    chatMessage = parser.networkToChatMessage(message);
                    if(chatMessage.getFileSize() > 0) {
                        Log.d(TAG, "we do not accept attached file yet");
                        continue;
                    }

                    /*
                     * since we cannot have the mac address of the remote device, we use the IP address
                     * instead.
                     */
                    String senderIP = packet.getAddress().getHostAddress();
                    EventBus.getDefault().post(new ChatMessageReceived(
                                    chatMessage,
                                    this)
                    );

                } catch (JSONException ignore) {
                    Log.d(TAG, "malformed JSON");
                    continue;
                }
            }
        } catch (IOException e) {
        } catch( UDPMulticastSocketException e) {
        }
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        try {
            switch (command.getCommandID()) {
                case SEND_CHAT_MESSAGE:
                    String json = parser.chatMessageToNetwork(((CommandSendChatMessage) command).getChatMessage());
                    ((UDPMulticastConnection)con).send(json.getBytes());
                    EventBus.getDefault().post(new CommandExecuted(this, command, true));
                    return true;
                default:
                    return false;
            }
        } catch (IOException ignore) {
            Log.d(TAG, "Fail to send message:",ignore);
        } catch (UDPMulticastSocketException ignore) {
        }
        EventBus.getDefault().post(new CommandExecuted(this, command, false));
        return false;
    }

    @Override
    public void stopWorker() {
        if(!working)
            return;

        this.working = false;
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
        } finally {
            EventBus.getDefault().unregister(this);
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
