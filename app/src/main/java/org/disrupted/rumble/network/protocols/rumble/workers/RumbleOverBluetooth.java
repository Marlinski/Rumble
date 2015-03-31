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
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.rumble.RumbleBTState;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.Block;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockHello;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockStatus;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.BufferMismatchBlockSize;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlock;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.SubtypeUnknown;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.SendStatusMessageCommand;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

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
        this.bluetoothNeighbour = new BluetoothNeighbour(con.getRemoteMacAddress());
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
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }

    @Override
    public String getWorkerIdentifier() {
        return getProtocolIdentifier()+" "+con.getConnectionID();
    }

    @Override
    public void cancelWorker() {
        RumbleBTState connectionState = protocol.getBTState(con.getRemoteMacAddress());
        if(working) {
            Log.d(TAG, "[!] should not call cancelWorker() on a working Worker, call stopWorker() instead !");
            stopWorker();
            if (connectionState.getState().equals(RumbleBTState.RumbleBluetoothState.CONNECTED)) {
                connectionState.notConnected();
                EventBus.getDefault().post(new NeighbourDisconnected(
                                new BluetoothNeighbour(con.getRemoteMacAddress()),
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

        RumbleBTState connectionState = protocol.getBTState(con.getRemoteMacAddress());
        try {
            con.connect();

            connectionState.connected(this.getWorkerIdentifier());

            EventBus.getDefault().post(new NeighbourConnected(
                    new BluetoothNeighbour(con.getRemoteMacAddress()),
                    getProtocolIdentifier() )
            );

            /*
             * this method automatically creates two threads that runs in a while(true) loop,
             *   - one for processing the command from the upper layer
             *   - one for processing the packet received by the link layer layer
             */
            onWorkerConnected();

        } catch (RumbleBTState.StateException e) {
            Log.e(TAG, "[!] FAILED: "+getWorkerIdentifier()+" impossible connection state: "+connectionState.printState());
            return;
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED: "+getWorkerIdentifier()+" "+exception.getMessage());
        } finally {
            stopWorker();
        }

        if(connectionState.getState().equals(RumbleBTState.RumbleBluetoothState.CONNECTED)) {
            connectionState.notConnected();
            EventBus.getDefault().post(new NeighbourDisconnected(
                            new BluetoothNeighbour(con.getRemoteMacAddress()),
                            getProtocolIdentifier())
            );
        } else {
            connectionState.notConnected();
        }
    }

    @Override
    protected void processingPacketFromNetwork(){
        try {
            byte[] buffer = new byte[BlockHeader.HEADER_LENGTH];
            byte[] payload = null;
            while (true) {
                try {
                    Log.d(TAG, "processing... ");
                    long timeToTransfer = System.currentTimeMillis();

                /* receiving header */
                    int count = con.getInputStream().read(buffer, 0, BlockHeader.HEADER_LENGTH);
                    if (count < BlockHeader.HEADER_LENGTH)
                        throw new MalformedRumblePacket();
                    BlockHeader header = new BlockHeader(buffer);
                    header.readBuffer();

                /* receiving payload */
                    if (header.getPayloadLength() > 0) {
                        payload = new byte[header.getPayloadLength()];
                        count = con.getInputStream().read(payload, 0, header.getPayloadLength());
                        if (count < header.getPayloadLength())
                            throw new MalformedRumblePacket();
                    } else {
                        payload = null;
                    }

                    Log.d(TAG, "version: "+header.getVersion() +
                            " type: "+header.getType() +
                            " subtype: "+ header.getSubtype() +
                            " length: "+header.getPayloadLength());

                /* processing block */
                    switch (header.getSubtype()) {
                        case (BlockHello.SUBTYPE):
                            BlockHello blockHello = new BlockHello(header);
                            break;
                        case (BlockStatus.SUBTYPE):
                            if(payload == null)
                                throw new MalformedRumblePacket();
                            BlockStatus blockStatus = new BlockStatus(header, payload);
                            blockStatus.readBuffer();
                            StatusMessage statusMessage = blockStatus.getMessage();

                            /*
                             * It is very important to post an event as it will be catch by the
                             * CacheManager and will update the database accordingly
                             */
                            timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                            List<String> sender = new LinkedList<String>();
                            sender.add(con.getRemoteMacAddress());
                            EventBus.getDefault().post(new StatusSentEvent(
                                            statusMessage,
                                            sender,
                                            RumbleProtocol.protocolID,
                                            BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                                            blockStatus.getBytes().length,
                                            timeToTransfer)
                            );

                            Log.d(TAG, "Received Rumble message: " + statusMessage.toString());
                            break;
                        default:
                            throw new SubtypeUnknown(header.getSubtype());
                    }

                } catch (MalformedRumblePacket e) {
                    Log.d(TAG, "[!] malformed packet, received data are shorter than expected");
                } catch (BufferMismatchBlockSize e) {
                    Log.d(TAG, "[!] buffer is too short, check protocol version");
                } catch (SubtypeUnknown e) {
                    Log.d(TAG, "[!] packet subtype is unknown: " + e.subtype);
                } catch (MalformedBlock e) {
                    Log.d(TAG, "[!] cannot get the message");
                } finally {
                    payload = null;
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
            if (statusMessage.isForwarder(con.getRemoteMacAddress(), RumbleProtocol.protocolID))
                return false;

            Log.d(TAG, "[+] sending status "+statusMessage.toString());

            Block block = new BlockStatus(new BlockHeader(), statusMessage);
            try {
                long timeToTransfer = System.currentTimeMillis();

                con.getOutputStream().write(block.getBytes());

                /*
                 * It is very important to post an event as it will be catch by the
                 * CacheManager and will update the database accordingly
                 */
                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                List<String> recipients = new LinkedList<String>();
                recipients.add(con.getRemoteMacAddress());
                EventBus.getDefault().post(new StatusSentEvent(
                                statusMessage,
                                recipients,
                                RumbleProtocol.protocolID,
                                BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                                block.getBytes().length,
                                timeToTransfer)
                );

                // /!\ remove that
                long throughput = (block.getBytes().length / (timeToTransfer == 0 ? 1: timeToTransfer));
                Log.d(TAG, "Status Sent ("+con.getRemoteMacAddress()+","+(throughput/1000L)+"): " + statusMessage.toString());
                try {
                    Thread.sleep(1000);
                }catch(InterruptedException e) {}
            }
            catch( MalformedRumblePacket ignore ){
                Log.e(TAG, "[!] malformed packet");
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
