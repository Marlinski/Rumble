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

import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.events.NeighbourConnected;
import org.disrupted.rumble.network.events.NeighbourDisconnected;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClientConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTState;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.Block;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockFile;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockStatus;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;

import java.io.IOException;
import java.io.InputStream;

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

    public BluetoothNeighbour getBluetoothNeighbour() {
        return bluetoothNeighbour;
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
            Log.d(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
            if (connectionState.getState().equals(RumbleBTState.RumbleBluetoothState.CONNECTED)) {
                connectionState.notConnected();
                EventBus.getDefault().post(new NeighbourDisconnected(
                                new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                                getProtocolIdentifier())
                );
            } else {
                connectionState.notConnected();
            }
        } else
            connectionState.notConnected();
    }

    @Override
    public void startWorker() {
        if(working)
            return;
        working = true;

        RumbleBTState connectionState = protocol.getBTState(con.getRemoteLinkLayerAddress());

        try {
            // this is to prevent race condition that happen when server
            // and client connect at the same time
            connectionState.lockWorker.lock();

            con.connect();
            // hack to synchronise the client and server
            if(con instanceof BluetoothClientConnection)
                con.getInputStream().read(new byte[1],0,1);

            connectionState.connected(this.getWorkerIdentifier());
            EventBus.getDefault().post(new NeighbourConnected(
                            new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                            getProtocolIdentifier())
            );
            protocol.workerConnected(this);
        } catch (IOException exception) {
            Log.e(TAG, "[!] FAILED CON: " + getWorkerIdentifier() + connectionState.printState()+exception.getMessage());
        } catch (LinkLayerConnectionException exception) {
            Log.e(TAG, "[!] FAILED CON: " + getWorkerIdentifier() + connectionState.printState()+exception.getMessage());
        } catch (RumbleBTState.StateException e) {
            Log.e(TAG, "[!] FAILED STATE: " + getWorkerIdentifier() + connectionState.printState());
        } finally {
            connectionState.lockWorker.unlock();
        }

        try {
            onWorkerConnected();

            protocol.workerDisconnected(this);
            connectionState.notConnected();
            EventBus.getDefault().post(new NeighbourDisconnected(
                            new BluetoothNeighbour(con.getRemoteLinkLayerAddress()),
                            getProtocolIdentifier())
            );
        } finally {
            stopWorker();
        }
    }

    @Override
    protected void processingPacketFromNetwork(){
        try {
            while (true) {
                try {
                    BlockHeader header = BlockHeader.readBlock(con.getInputStream());
                    Block block = null;
                    switch (header.getBlockType()) {
                        case BlockHeader.BLOCKTYPE_STATUS:
                            block = new BlockStatus(header);
                            break;
                        case BlockHeader.BLOCKTYPE_FILE:
                            block = new BlockFile(header);
                            break;
                    }
                    if(block != null) {
                        block.readBlock(con); // it reads and process the block
                        block.dismiss();
                    }
                } catch (MalformedRumblePacket e) {
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
    public boolean isCommandSupported(String commandName) {
        if(commandName.equals(SendStatusMessageCommand.COMMAND_NAME))
            return true;
        return false;
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        if(!isCommandSupported(command.getCommandName()))
            return false;

        if(command instanceof SendStatusMessageCommand) {
            StatusMessage statusMessage = ((SendStatusMessageCommand) command).getStatus();
            //todo ce n'est pas ici de prendre cette decision
            if (statusMessage.isForwarder(con.getRemoteLinkLayerAddress(), RumbleProtocol.protocolID))
                return false;
            Log.d(TAG, "[+] sending status "+statusMessage.toString());
            Block blockStatus = new BlockStatus(statusMessage);
            try {
                long total = blockStatus.writeBlock(con);
            }
            catch(IOException ignore){
                Log.e(TAG, "[!] error while sending");
            }
            catch(InputOutputStreamException ignore) {
                Log.e(TAG, "[!] error while sending");
            }
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
            Log.d(TAG, "[-]"+ignore.getMessage());
        }
    }

}
