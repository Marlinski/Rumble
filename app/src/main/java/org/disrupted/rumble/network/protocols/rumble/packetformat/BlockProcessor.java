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

package org.disrupted.rumble.network.protocols.rumble.packetformat;

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.events.FileReceived;
import org.disrupted.rumble.network.protocols.events.PushStatusReceived;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlock;
import org.disrupted.rumble.util.EncryptedInputStream;
import org.disrupted.rumble.util.CryptoUtil;

import java.io.IOException;
import java.io.InputStream;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class BlockProcessor {

    public static final String TAG = "BlockProcessor";

    /* necessary attributes */
    private InputStream in;
    private ProtocolChannel channel;

    /* bundle context, reset at the end of every bundle (when last_block flag is set) */
    private EncryptedInputStream eis;
    private BlockPushStatus blockPushStatus;

    public BlockProcessor(InputStream in, ProtocolChannel channel) {
        this.in = in;
        this.channel = channel;
        resetContext();
    }

    public void resetContext() {
        try {
            if (eis != null)
                eis.close();
        } catch(IOException e){ //ignore
        }
        eis = null;
        blockPushStatus = null;
    }

    public void processBlock(BlockHeader header) throws IOException, InputOutputStreamException, MalformedBlock {
        long timeToTransfer = System.nanoTime();

        if(header.isEncrypted() && (eis == null)) {
            BlockNull nullBlock = new BlockNull(header);
            channel.bytes_received += nullBlock.readBlock(in);
            channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
        } else {
            InputStream is = in;
            if(header.isEncrypted()) {
                eis.setLimit((int)header.getBlockLength());
                is = eis;
            }
            switch (header.getBlockType()) {
                case BlockHeader.BLOCKTYPE_PUSH_STATUS:
                    BlockPushStatus blockStatus = new BlockPushStatus(header);
                    channel.bytes_received += blockStatus.readBlock(is);
                    channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
                    if(!blockStatus.status.hasAttachedFile()) {
                        channel.status_received++;
                        EventBus.getDefault().post(new PushStatusReceived(
                                        blockStatus.status,
                                        blockStatus.group_id_base64,
                                        blockStatus.sender_id_base64,
                                        "",
                                        RumbleProtocol.protocolID,
                                        channel.getLinkLayerIdentifier())
                        );
                        blockPushStatus = null;
                    } else {
                        blockPushStatus = blockStatus;
                    }
                    break;
                case BlockHeader.BLOCKTYPE_FILE:
                    BlockFile blockFile = new BlockFile(header);
                    channel.bytes_received += blockFile.readBlock(is);
                    channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
                    if(blockPushStatus != null) {
                        channel.status_received++;
                        EventBus.getDefault().post(new PushStatusReceived(
                                        blockPushStatus.status,
                                        blockPushStatus.group_id_base64,
                                        blockPushStatus.sender_id_base64,
                                        blockFile.filename,
                                        RumbleProtocol.protocolID,
                                        channel.getLinkLayerIdentifier())
                        );
                    } else {
                        EventBus.getDefault().post(new FileReceived(
                                        blockFile.filename,
                                        blockFile.status_id_base64,
                                        RumbleProtocol.protocolID,
                                        channel.getLinkLayerIdentifier())
                        );
                    }
                    break;
                case BlockHeader.BLOCKTYPE_CONTACT:
                    BlockContact blockContact = new BlockContact(header);
                    channel.bytes_received += blockContact.readBlock(is);
                    channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
                    UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
                    EventBus.getDefault().post(new ContactInformationReceived(
                                    blockContact.contact,
                                    blockContact.flags,
                                    channel,
                                    con.getLinkLayerNeighbour())
                    );
                    break;
                case BlockHeader.BLOCKTYPE_CHAT_MESSAGE:
                    BlockChatMessage blockChatMessage = new BlockChatMessage(header);
                    channel.bytes_received += blockChatMessage.readBlock(is);
                    channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
                    EventBus.getDefault().post(new ChatMessageReceived(
                                    blockChatMessage.chatMessage,
                                    channel)
                    );
                    break;
                case BlockHeader.BLOCKTYPE_KEEPALIVE:
                    BlockKeepAlive blockKA = new BlockKeepAlive(header);
                    channel.bytes_received += blockKA.readBlock(is);
                    channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
                    break;
                case BlockHeader.BLOCK_CIPHER:
                    BlockCipher blockCipher = new BlockCipher(header);
                    channel.bytes_received += blockCipher.readBlock(is);
                    channel.in_transmission_time += (System.nanoTime() - timeToTransfer);
                    if (blockCipher.type.equals(BlockCipher.CipherType.TYPE_CIPHER_GROUP)
                            && (blockCipher.group_id_base64 != null)
                            && (blockCipher.ivBytes != null)){
                        try {
                            Group group = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext())
                                    .getGroup(blockCipher.group_id_base64);
                            if (group == null)
                                throw new CryptoUtil.CryptographicException();
                            eis = CryptoUtil.getCipherInputStream(
                                    in,
                                    blockCipher.algo,
                                    blockCipher.block,
                                    blockCipher.padding,
                                    group.getGroupKey(),
                                    blockCipher.ivBytes);
                        } catch (CryptoUtil.CryptographicException e) {
                            eis = null;
                        }
                    } else {
                        eis = null;
                    }
                    blockCipher.dismiss();
                    break;
                default:
                    throw new MalformedBlock("Unknown header type: " + header.getBlockType(), 0);
            }
        }

        if(header.isLastBlock()) {
            resetContext();
        }
    }

}
