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

package org.disrupted.rumble.network.protocols.rumble.packetformat;

import android.util.Base64;

import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.CommandSendChatMessage;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.network.protocols.events.ChatMessageSent;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.HashUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * A BlockStatus holds all the information necessary to retrieve a Status
 *
 * +-------------------------------------------+
 * |               User ID                     |  8 byte Author UID
 * +--------+----------------------------------+
 * | Length |         Author (String)          |  1 byte + VARIABLE
 * +--------+---------+------------------------+
 * |      Length      |     Message (String)   |  2 bytes + VARIABLE
 * +------------------+------------------------+
 *
 * @author Marlinski
 */
public class BlockChatMessage extends Block {

    private static final String TAG = "BlockChatMessage";

    /*
     * Byte size
     */
    private static final int FIELD_UID_SIZE             = HashUtil.USER_ID_SIZE;
    private static final int FIELD_AUTHOR_LENGTH_SIZE   = 1;
    private static final int FIELD_STATUS_LENGTH_SIZE   = 2;


    private  static final int MIN_PAYLOAD_SIZE = (
            FIELD_UID_SIZE +
            FIELD_AUTHOR_LENGTH_SIZE +
            FIELD_STATUS_LENGTH_SIZE);

    private static final int MAX_CHAT_MESSAGE_SIZE = 255; // limiting status to 500 character;
    private static final int MAX_BLOCK_CHAT_MESSAGE_SIZE = MIN_PAYLOAD_SIZE + 255*2 + MAX_CHAT_MESSAGE_SIZE;

    private ChatMessage chatMessage;

    public BlockChatMessage(CommandSendChatMessage command) {
        super(new BlockHeader());
        this.header.setBlockType(BlockHeader.BLOCKTYPE_CHAT_MESSAGE);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);
        this.chatMessage = command.getChatMessage();
    }

    public BlockChatMessage(BlockHeader header) {
        super(header);
        this.chatMessage = null;
    }

    @Override
    public long readBlock(ProtocolChannel channel) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_CHAT_MESSAGE)
            throw new MalformedBlockPayload("Block type BLOCK_CHAT expected", 0);

        long readleft = header.getBlockLength();
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_BLOCK_CHAT_MESSAGE_SIZE))
            throw new MalformedBlockPayload("wrong header length parameter: "+readleft, 0);

        long timeToTransfer = System.currentTimeMillis();


        /* read the block */
        InputStream in = con.getInputStream();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        int count = in.read(blockBuffer, 0, (int)header.getBlockLength());
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < (int)header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected", count);

        /* process the read buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            byte[] author_id = new byte[FIELD_UID_SIZE];
            byteBuffer.get(author_id, 0, FIELD_UID_SIZE);
            readleft -= FIELD_UID_SIZE;

            short authorLength = byteBuffer.get();
            readleft -= FIELD_AUTHOR_LENGTH_SIZE;
            if ((authorLength <= 0) || (authorLength > readleft))
                throw new MalformedBlockPayload("wrong author.length parameter: " + authorLength, header.getBlockLength()-readleft);
            byte[] author_name = new byte[authorLength];
            byteBuffer.get(author_name, 0, authorLength);
            readleft -= authorLength;

            short messageLength = byteBuffer.getShort();
            readleft -= FIELD_STATUS_LENGTH_SIZE;
            if ((messageLength <= 0) || (messageLength > readleft))
                throw new MalformedBlockPayload("wrong message.length parameter: " + messageLength, header.getBlockLength()-readleft);
            byte[] message = new byte[messageLength];
            byteBuffer.get(message, 0, messageLength);
            readleft -= messageLength;

            long receivedAt = (System.currentTimeMillis() / 1000L);

            if(readleft > 0)
                throw new MalformedBlockPayload("wrong header.length parameter, no more data to read: " + (header.getBlockLength()-readleft), header.getBlockLength()-readleft);

            /* assemble the status */
            String author_id_base64 = Base64.encodeToString(author_id, 0, FIELD_UID_SIZE, Base64.NO_WRAP);

            Contact contact_tmp  = new Contact(new String(author_name),author_id_base64,false);
            chatMessage = new ChatMessage(contact_tmp, new String(message), receivedAt, RumbleProtocol.protocolID);

            timeToTransfer = (System.currentTimeMillis() - timeToTransfer);
            EventBus.getDefault().post(new ChatMessageReceived(
                            chatMessage,
                            con.getRemoteLinkLayerAddress(),
                            RumbleProtocol.protocolID,
                            con.getLinkLayerIdentifier(),
                            header.getBlockLength(),
                            timeToTransfer)
            );

            return header.getBlockLength();
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", header.getBlockLength() - readleft);
        }
    }

    @Override
    public long writeBlock(ProtocolChannel channel) throws IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        long timeToTransfer = System.currentTimeMillis();

        /* calculate the total block size */
        byte[] post     = chatMessage.getMessage().getBytes(Charset.forName("UTF-8"));
        byte[] author_name = chatMessage.getAuthor().getName().getBytes(Charset.forName("UTF-8"));
        byte[] author_id   = Base64.decode(chatMessage.getAuthor().getUid(),Base64.NO_WRAP);

        int length = MIN_PAYLOAD_SIZE +
                author_name.length +
                post.length;
        header.setPayloadLength(length);

        BlockFile blockFile = null;
        /*
        if(chatMessage.hasAttachedFile()) {
            header.setLastBlock(false);
            blockFile = new BlockFile(status.getFileName(), status.getUuid());
        }
        */

        /* prepare the buffer */
        ByteBuffer blockBuffer = ByteBuffer.allocate(length);
        blockBuffer.put(author_id, 0, FIELD_UID_SIZE);
        blockBuffer.put((byte)author_name.length);
        blockBuffer.put(author_name, 0, author_name.length);
        blockBuffer.putShort((short) post.length);
        blockBuffer.put(post, 0, post.length);

        /* send the header, the status and the attached file */
        header.writeBlock(con.getOutputStream());
        con.getOutputStream().write(blockBuffer.array(),0,length);
        if(blockFile != null)
            blockFile.writeBlock(channel);

        /*
         * It is very important to post an event as it will be catch by the
         * CacheManager and will update the database accordingly
         */
        timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
        List<String> recipients = new ArrayList<String>();
        recipients.add(con.getRemoteLinkLayerAddress());
        EventBus.getDefault().post(new ChatMessageSent(
                        chatMessage,
                        recipients,
                        RumbleProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH,
                        timeToTransfer)
        );

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {

    }
}
