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

package org.disrupted.rumble.network.protocols.rumble.workers;

import android.util.Log;

import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.events.NeighbourConnected;
import org.disrupted.rumble.network.events.NeighbourDisconnected;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClientConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTState;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.Block;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockContact;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockFile;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockPushStatus;
import org.disrupted.rumble.network.protocols.rumble.packetformat.NullBlock;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.network.protocols.command.Command;

import java.io.IOException;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class RumbleOverBluetooth extends ProtocolWorker {

    private static final String TAG = "RumbleOverBluetooth";

    private final BluetoothConnection con;
    private final RumbleProtocol protocol;
    protected boolean working;

    private BluetoothNeighbour bluetoothNeighbour;

    public RumbleOverBluetooth(RumbleProtocol protocol, BluetoothConnection con) {
        this.protocol = protocol;
        this.con = con;
        this.working = false;
        this.bluetoothNeighbour = (BluetoothNeighbour)con.getLinkLayerNeighbour();
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
    public LinkLayerConnection getLinkLayerConnection() {
        return con;
    }

    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public void cancelWorker() {
        RumbleBTState connectionState = protocol.getBTState(con.getRemoteLinkLayerAddress());
        if(working) {
            Log.e(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
        } else
            connectionState.notConnected();
    }



    @Override
    public void startWorker() {
        if (working)
            return;
        working = true;

        RumbleBTState connectionState = protocol.getBTState(con.getRemoteLinkLayerAddress());

        try {

            if (con instanceof BluetoothClientConnection) {
                if (!connectionState.getState().equals(RumbleBTState.RumbleBluetoothState.CONNECTION_SCHEDULED))
                    throw new RumbleBTState.StateException();

                ((BluetoothClientConnection) con).waitScannerToStop();

                try {
                    connectionState.lock.lock();
                    connectionState.connectionInitiated(getWorkerIdentifier());
                } catch (RumbleBTState.StateException e) {
                    throw e;
                } finally {
                    connectionState.lock.unlock();
                }
            }

            // maybe implement a form of randomized delay ?
            con.connect();

            try {
                connectionState.lock.lock();
                connectionState.connected(getWorkerIdentifier());
            } catch (RumbleBTState.StateException e) {
                throw e;
            } finally {
                connectionState.lock.unlock();
            }

            // hack to synchronise the client and server
            if (con instanceof BluetoothClientConnection)
                con.getInputStream().read(new byte[1], 0, 1);

        } catch (RumbleBTState.StateException state) {
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
            EventBus.getDefault().post(new NeighbourConnected(
                            new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                            this)
            );
            onWorkerConnected();
        } finally {
            Log.d(TAG, "[+] disconnected");
            EventBus.getDefault().post(new NeighbourDisconnected(
                            new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                            this)
            );
            stopWorker();
            connectionState.notConnected();
        }
    }

    @Override
    protected void processingPacketFromNetwork(){
        try {
            while (true) {
                try {
                    BlockHeader header = BlockHeader.readBlock(con.getInputStream());
                    Block block;
                    switch (header.getBlockType()) {
                        case BlockHeader.BLOCKTYPE_STATUS:
                            block = new BlockPushStatus(header);
                            break;
                        case BlockHeader.BLOCKTYPE_FILE:
                            block = new BlockFile(header);
                            break;
                        case BlockHeader.BLOCKTYPE_CONTACT:
                            block = new BlockContact(header);
                            break;
                        default:
                            block = new NullBlock(header);
                            break;
                    }

                    long bytesread = 0;
                    try {
                        bytesread = block.readBlock(con);
                    } catch ( MalformedBlockPayload e ) {
                        bytesread = e.bytesRead;
                    }

                    if(bytesread < header.getBlockLength()) {
                        byte[] buffer = new byte[1024];
                        long readleft = header.getBlockLength();
                        while(readleft > 0) {
                            long max_read = Math.min((long)1024,readleft);
                            int read = con.getInputStream().read(buffer, 0, (int)max_read);
                            readleft -= read;
                        }
                    }

                    block.dismiss();
                } catch (MalformedBlockHeader e) {
                    Log.d(TAG, "[!] malformed packet: "+e.getMessage());
                }
            }
        } catch (IOException silentlyCloseConnection) {
            Log.d(TAG, silentlyCloseConnection.getMessage());
        } catch (InputOutputStreamException silentlyCloseConnection) {
            Log.d(TAG, silentlyCloseConnection.getMessage());
        }
    }

    @Override
    public boolean isCommandSupported(Command.CommandID commandID) {
        switch (commandID) {
            case SEND_LOCAL_INFORMATION:
            case SEND_PUSH_STATUS:
                return true;
            default:
                return  false;
        }
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        Block block;
        try {
            switch (command.getCommandID()) {
                case SEND_LOCAL_INFORMATION:
                    block = new BlockContact((CommandSendLocalInformation) command);
                    break;
                case SEND_PUSH_STATUS:
                    block = new BlockPushStatus((CommandSendPushStatus) command);
                    break;
                default:
                    return false;
            }
            block.writeBlock(con);
            block.dismiss();
            return true;
        }
        catch(Exception ignore){
            Log.e(TAG, "[!] error while sending");
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
    }

}
