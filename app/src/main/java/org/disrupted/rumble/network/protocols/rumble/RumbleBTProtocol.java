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

package org.disrupted.rumble.network.protocols.rumble;

import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.NetworkThread;
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.network.exceptions.ProtocolNotFoundException;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.GenericProtocol;
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
public class RumbleBTProtocol extends GenericProtocol implements NetworkThread {

    private static final String TAG = "RumbleBTProtocol";

    private BluetoothConnection con;
    protected boolean isBeingKilled;

    public RumbleBTProtocol(BluetoothConnection con) {
        this.con = con;
        this.isBeingKilled = false;
    }

    @Override
    public String getNetworkThreadID() {
        return getProtocolID()+" "+con.getConnectionID();
    }
    @Override
    public String getProtocolID() {
        return RumbleProtocol.protocolID;
    }
    @Override
    public String getType() {
        return con.getLinkLayerIdentifier();
    }
    @Override
    public String getLinkLayerIdentifier() {
        return con.getLinkLayerIdentifier();
    }


    @Override
    public void run() {

        try {
            con.connect();
        } catch (LinkLayerConnectionException exception) {
            Log.d(TAG, "[!] FAILED: "+getNetworkThreadID()+" "+exception.getMessage());
            return;
        }

        try {
            NetworkCoordinator.getInstance().addProtocol(con.getRemoteMacAddress(), this);

            Log.d(TAG, "[+] ESTABLISHED: " + getNetworkThreadID());

            /*
             * this one automatically creates two thread, one for processing the command
             * and one for processing the network
             */
            onGenericProcotolConnected();

        } catch (RecordNotFoundException ignoredCauseImpossible) {
            Log.e(TAG, "[+] FAILED: "+getNetworkThreadID()+" cannot find the record for "+con.getRemoteMacAddress());
        }
        finally {
            try {
                NetworkCoordinator.getInstance().delProtocol(con.getRemoteMacAddress(), this);
            } catch (RecordNotFoundException ignoredCauseImpossible) {
            } catch (ProtocolNotFoundException ignoredCauseImpossible) {
            }

            if (!isBeingKilled)
                kill();
        }
    }

    @Override
    protected void processingPacketFromNetwork(){
        try {
            byte[] buffer = new byte[BlockHeader.HEADER_LENGTH];
            byte[] payload = null;
            while (true) {
                try {
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
                            statusMessage.addForwarder(con.getRemoteMacAddress(), "Rumble");
                            Log.d(TAG, "Received Rumble message: " + statusMessage.toString());
                            DatabaseFactory.getStatusDatabase(RumbleApplication.getContext()).insertStatus(statusMessage, null);
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
        } catch (InputOutputStreamException silentlyCloseConnection) {
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
            if (statusMessage.isForwarder(con.getRemoteMacAddress(), RumbleProtocol.protocolID))
                return false;

            Block block = new BlockStatus(new BlockHeader(), statusMessage);
            try {
                long timeToTransfer = System.currentTimeMillis();
                long bytesTransfered = block.getBytes().length;

                con.getOutputStream().write(block.getBytes());

                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                long throughput = (bytesTransfered / (timeToTransfer == 0 ? 1: timeToTransfer));
                List<String> recipients = new LinkedList<String>();
                recipients.add(con.getRemoteMacAddress());
                EventBus.getDefault().post(new StatusSentEvent(
                                statusMessage,
                                recipients,
                                RumbleProtocol.protocolID,
                                BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                                throughput)
                );

                //remove that
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
    public void stop() {
    }

    @Override
    public void kill() {
        this.isBeingKilled = true;
        try {
            con.disconnect();
        } catch (LinkLayerConnectionException e) {
            Log.e(TAG, e.getMessage());
        }
        Log.d(TAG, "[-] ENDED: " + getNetworkThreadID());
    }
}
