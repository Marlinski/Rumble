/*
 * Copyright (C) 2014 Lucien Loiseau
 * This file is part of Rumble.
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
 * You should have received a copy of the GNU General Public License along
 * with Rumble.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.disrupted.rumble.network.protocols.rumble.workers;

import android.os.Handler;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClientConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothServerConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.CommandSendChatMessage;
import org.disrupted.rumble.network.protocols.command.CommandSendKeepAlive;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.events.CommandExecuted;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.events.ChannelConnected;
import org.disrupted.rumble.network.events.ChannelDisconnected;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.RumbleStateMachine;
import org.disrupted.rumble.network.protocols.rumble.packetformat.Block;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockChatMessage;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockContact;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockCipher;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockFile;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockKeepAlive;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockProcessor;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockPushStatus;
import org.disrupted.rumble.network.protocols.rumble.packetformat.CommandProcessor;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlock;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockHeader;
import org.disrupted.rumble.util.CryptoUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;


import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class RumbleUnicastChannel extends ProtocolChannel {

    private static final String TAG = "RumbleUnicastChannel";

    private static final int KEEP_ALIVE_TIME = 2000;
    private static final int SOCKET_TIMEOUT_UDP  = 5000;
    private static final int SOCKET_TIMEOUT_BLUETOOTH  = 20000;

    private boolean working;
    private Contact remoteContact;

    private BlockProcessor   blockProcessor;
    private CommandProcessor commandProcessor;
    private Handler keepAlive;
    private Handler socketTimeout;

    public RumbleUnicastChannel(RumbleProtocol protocol, UnicastConnection con) {
        super(protocol, con);
        remoteContact = null;
        keepAlive     = new Handler(protocol.getNetworkCoordinator().getServiceLooper());
        socketTimeout = new Handler(protocol.getNetworkCoordinator().getServiceLooper());
    }

    @Override
    public void cancelWorker() {
        RumbleStateMachine connectionState = ((RumbleProtocol)protocol).getState(
                con.getLinkLayerNeighbour().getLinkLayerAddress());
        if(working) {
            Log.e(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
        } else
            connectionState.notConnected();
    }

    @Override
    public void startWorker() {
        if(isWorking())
            return;
        working = true;
        EventBus.getDefault().register(this);

        RumbleProtocol     rumbleProtocol = (RumbleProtocol)protocol;
        RumbleStateMachine connectionState = rumbleProtocol.getState(
                con.getLinkLayerNeighbour().getLinkLayerAddress());

        try {
            if (con instanceof BluetoothClientConnection) {
                if (!connectionState.getState().equals(RumbleStateMachine.RumbleState.CONNECTION_SCHEDULED))
                    throw new RumbleStateMachine.StateException();

                ((BluetoothClientConnection) con).waitScannerToStop();
            }

            con.connect();

            try {
                connectionState.lock.lock();
                connectionState.connected(getWorkerIdentifier());
            } finally {
                connectionState.lock.unlock();
            }

            /*
             * Bluetooth hack to synchronise the client and server
             * if I don't do this, they sometime fail to connect ? :/ ?
             */
            if (con instanceof BluetoothServerConnection)
                ((BluetoothConnection)con).getOutputStream().write(new byte[]{0},0,1);
            if (con instanceof BluetoothClientConnection)
                ((BluetoothConnection)con).getInputStream().read(new byte[1], 0, 1);

        } catch (RumbleStateMachine.StateException state) {
            Log.e(TAG, "[-] client connected while trying to connect");
            stopWorker();
            return;
        } catch (LinkLayerConnectionException llce) {
            Log.e(TAG, "[!] FAILED CON: " + getWorkerIdentifier() + " - " + llce.getMessage());
            stopWorker();
            connectionState.notConnected();
            return;
        } catch (IOException io) {
            Log.e(TAG, "[!] FAILED CON: " + getWorkerIdentifier() + " - " + io.getMessage());
            stopWorker();
            connectionState.notConnected();
            return;
        }

        try {
            Log.d(TAG, "[+] connected");
            EventBus.getDefault().post(new ChannelConnected(
                            con.getLinkLayerNeighbour(),
                            this)
            );

            onChannelConnected();
        } finally {
            Log.d(TAG, "[+] disconnected");
            EventBus.getDefault().post(new ChannelDisconnected(
                            con.getLinkLayerNeighbour(),
                            this,
                            error)
            );
            stopWorker();
            connectionState.notConnected();
        }
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    protected void processingPacketFromNetwork(){
        try {
            InputStream in = ((UnicastConnection)this.getLinkLayerConnection()).getInputStream();
            blockProcessor = new BlockProcessor(in, this);
            while (true) {
                // read next block header (blocking)
                BlockHeader header = BlockHeader.readBlockHeader(in);

                // channel is alive, cancel timeout during block processing
                socketTimeout.removeCallbacks(socketTimeoutFires);

                // process block
                blockProcessor.processBlock(header);

                // set timeout
                if(con instanceof BluetoothConnection)
                    socketTimeout.postDelayed(socketTimeoutFires, SOCKET_TIMEOUT_BLUETOOTH);
                else
                    socketTimeout.postDelayed(socketTimeoutFires, SOCKET_TIMEOUT_UDP);
            }
        } catch (IOException silentlyCloseConnection) {
            Log.d(TAG, " "+silentlyCloseConnection.getMessage());
        } catch (InputOutputStreamException silentlyCloseConnection) {
            Log.d(TAG, " "+silentlyCloseConnection.getMessage());
        } catch (MalformedBlock e) {
            error = true;
            Log.d(TAG, "[!] malformed block: " + e.reason + "("+e.bytesRead+")");
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        try {
            if(commandProcessor == null)
                commandProcessor = new CommandProcessor(((UnicastConnection)this.getLinkLayerConnection()).getOutputStream(), this);

            // remove keep alive if any
            keepAlive.removeCallbacks(keepAliveFires);

            commandProcessor.processCommand(command);

            if(!command.getCommandID().equals(Command.CommandID.SEND_KEEP_ALIVE))
                EventBus.getDefault().post(new CommandExecuted(this, command, true));

            // schedule a keep alive to send
            keepAlive.postDelayed(keepAliveFires, KEEP_ALIVE_TIME);

            //EventBus.getDefault().post(new CommandExecuted(this, command, false));
            return true;
        } catch(InputOutputStreamException ignore) {
            ignore.printStackTrace();
            Log.d(TAG, "[!] "+command.getCommandID()+" "+ignore.getMessage());
        } catch(IOException ignore){
            ignore.printStackTrace();
            Log.d(TAG, "[!] "+command.getCommandID()+" "+ignore.getMessage());
        }
        return false;
    }

    @Override
    public void stopWorker() {
        if(!working)
            return;
        working = false;
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException ignore) {
            //Log.d(TAG, "[-]"+ignore.getMessage());
        }
        finally {
            keepAlive.removeCallbacks(keepAliveFires);
            socketTimeout.removeCallbacks(socketTimeoutFires);
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public Set<Contact> getRecipientList() {
        Set<Contact> ret = new HashSet<Contact>(1);
        if(remoteContact != null)
            ret.add(remoteContact);
        return ret;
    }
    public void onEvent(ContactInformationReceived event) {
        if(event.channel.equals(this))
            this.remoteContact = event.contact;
    }

    /*
     * keep-alive handler related method
     */
    private Runnable keepAliveFires = new Runnable() {
        @Override
        public void run() {
            CommandSendKeepAlive sendKeepAlive = new CommandSendKeepAlive();
            //executeNonBlocking(sendKeepAlive);
        }
    };
    private Runnable socketTimeoutFires = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "channel seems dead");
            //stopWorker();
        }
    };
}
