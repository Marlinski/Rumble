/*
 * Copyright (C) 2014 Disrupted Systems
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

import android.util.Log;

import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.command.CommandSendChatMessage;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.events.CommandExecuted;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.Block;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockChatMessage;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockContact;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockFile;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockPushStatus;
import org.disrupted.rumble.network.protocols.rumble.packetformat.NullBlock;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;

import java.io.IOException;


import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public abstract class RumbleUnicastChannel extends ProtocolChannel {

    private static final String TAG = "RumbleProtocolWorker";

    protected boolean working;

    public RumbleUnicastChannel(RumbleProtocol protocol, UnicastConnection con) {
        super(protocol, con);
    }

    @Override
    public boolean isWorking() {
        return working;
    }

    @Override
    protected void processingPacketFromNetwork(){
        try {
            while (true) {
                try {
                    BlockHeader header = BlockHeader.readBlock(((UnicastConnection) con).getInputStream());
                    Block block;
                    switch (header.getBlockType()) {
                        case BlockHeader.BLOCKTYPE_PUSH_STATUS:
                            block = new BlockPushStatus(header);
                            break;
                        case BlockHeader.BLOCKTYPE_FILE:
                            block = new BlockFile(header);
                            break;
                        case BlockHeader.BLOCKTYPE_CONTACT:
                            block = new BlockContact(header);
                            break;
                        case BlockHeader.BLOCKTYPE_CHAT_MESSAGE:
                            block = new BlockChatMessage(header);
                            break;
                        default:
                            block = new NullBlock(header);
                            break;
                    }

                    long bytesread = 0;
                    try {
                        bytesread = block.readBlock(this);
                    } catch ( MalformedBlockPayload e ) {
                        bytesread = e.bytesRead;
                    }

                    if(bytesread < header.getBlockLength()) {
                        byte[] buffer = new byte[1024];
                        long readleft = header.getBlockLength();
                        while(readleft > 0) {
                            long max_read = Math.min((long)1024,readleft);
                            int read = ((UnicastConnection)con).getInputStream().read(buffer, 0, (int)max_read);
                            readleft -= read;
                        }
                    }

                    block.dismiss();
                } catch (MalformedBlockHeader e) {
                    Log.d(TAG, "[!] malformed packet: " + e.getMessage());
                }
            }
        } catch (IOException silentlyCloseConnection) {
            Log.d(TAG, silentlyCloseConnection.getMessage());
        } catch (InputOutputStreamException silentlyCloseConnection) {
            Log.d(TAG, silentlyCloseConnection.getMessage());
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
                case SEND_CHAT_MESSAGE:
                    block = new BlockChatMessage((CommandSendChatMessage) command);
                    break;
                default:
                    return false;
            }
            block.writeBlock(this);
            block.dismiss();
            EventBus.getDefault().post(new CommandExecuted(this, command, true));
            return true;
        }
        catch(Exception ignore){
            Log.e(TAG, "[!] error while sending");
        }
        EventBus.getDefault().post(new CommandExecuted(this, command, false));
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
