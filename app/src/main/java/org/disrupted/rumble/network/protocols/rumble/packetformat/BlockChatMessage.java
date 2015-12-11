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

import android.util.Base64;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.CommandSendChatMessage;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.network.protocols.events.ChatMessageSent;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.EncryptedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * A BlockChat holds all the information necessary to retrieve a ChatMessage
 *
 * +-------------------------------------------+
 * |               User ID                     |  8 byte Author UID
   +-------------------------------------------+
 * +--------+----------------------------------+
 * | Length |         Author (String)          |  1 byte + VARIABLE
 * +--------+---------+------------------------+
 * |      Length      |     Message (String)   |  2 bytes + VARIABLE
 * +------------------+------------------------+
 * |             Time of Creation              |  8 bytes
 * +-------------------------------------------+
 *
 * @author Lucien Loiseau
 */
public class BlockChatMessage extends Block {

    private static final String TAG = "BlockChatMessage";

    /*
     * Byte size
     */
    private static final int FIELD_UID_SIZE             = Contact.CONTACT_UID_RAW_SIZE;
    private static final int FIELD_AUTHOR_LENGTH_SIZE   = 1;
    private static final int FIELD_STATUS_LENGTH_SIZE   = 2;
    private static final int FIELD_TOC_SIZE             = 8;


    private  static final int MIN_PAYLOAD_SIZE = (
            FIELD_UID_SIZE +
            FIELD_AUTHOR_LENGTH_SIZE +
            FIELD_STATUS_LENGTH_SIZE +
            FIELD_TOC_SIZE);

    private static final int MAX_PAYLOAD_SIZE = (
            MIN_PAYLOAD_SIZE +
            Contact.CONTACT_NAME_MAX_SIZE +
            ChatMessage.MSG_MAX_SIZE);

    public ChatMessage chatMessage;

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

    private void sanityCheck() throws MalformedBlockPayload{
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_CHAT_MESSAGE)
            throw new MalformedBlockPayload("Block type BLOCK_CHAT expected", 0);
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_PAYLOAD_SIZE))
            throw new MalformedBlockPayload("wrong header length parameter: "+header.getBlockType(), 0);
    }

    @Override
    public long readBlock(InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        sanityCheck();

        long timeToTransfer = System.nanoTime();

        /* read the block */
        long readleft = header.getBlockLength();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        int count = in.read(blockBuffer, 0, (int)header.getBlockLength());
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < (int)header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected", count);

        BlockDebug.d(TAG, "BlockChatMessage received ("+count+" bytes): "+ Arrays.toString(blockBuffer));

        /* process the read buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            byte[] author_id = new byte[FIELD_UID_SIZE];
            byteBuffer.get(author_id, 0, FIELD_UID_SIZE);
            readleft -= FIELD_UID_SIZE;

            short authorLength = byteBuffer.get();
            readleft -= FIELD_AUTHOR_LENGTH_SIZE;
            if ((authorLength <= 0) || (authorLength > readleft) || (authorLength > Contact.CONTACT_NAME_MAX_SIZE))
                throw new MalformedBlockPayload("wrong author.length parameter: " + authorLength, header.getBlockLength()-readleft);
            byte[] author_name = new byte[authorLength];
            byteBuffer.get(author_name, 0, authorLength);
            readleft -= authorLength;

            short messageLength = byteBuffer.getShort();
            readleft -= FIELD_STATUS_LENGTH_SIZE;
            if ((messageLength <= 0) || (messageLength > readleft) || (messageLength > ChatMessage.MSG_MAX_SIZE))
                throw new MalformedBlockPayload("wrong message.length parameter: " + messageLength, header.getBlockLength()-readleft);
            byte[] message = new byte[messageLength];
            byteBuffer.get(message, 0, messageLength);
            readleft -= messageLength;

            long toc = byteBuffer.getLong();
            readleft -= FIELD_TOC_SIZE;

            if(readleft > 0)
                throw new MalformedBlockPayload("wrong header.length parameter, no more data to read: " + (header.getBlockLength()-readleft), header.getBlockLength()-readleft);

            /* assemble the status */
            String author_id_base64 = Base64.encodeToString(author_id, 0, FIELD_UID_SIZE, Base64.NO_WRAP);
            Contact contact_tmp  = new Contact(new String(author_name),author_id_base64,false);
            long receivedAt = (System.currentTimeMillis());
            chatMessage = new ChatMessage(contact_tmp, new String(message), toc, receivedAt, RumbleProtocol.protocolID);

            return header.getBlockLength();
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", header.getBlockLength() - readleft);
        }
    }

    @Override
    public long writeBlock(OutputStream out, EncryptedOutputStream eos) throws IOException, InputOutputStreamException {
        /* calculate the total block size */
        byte[] post     = chatMessage.getMessage().getBytes(Charset.forName("UTF-8"));
        byte[] author_name = chatMessage.getAuthor().getName().getBytes(Charset.forName("UTF-8"));
        byte[] author_id   = Base64.decode(chatMessage.getAuthor().getUid(),Base64.NO_WRAP);

        int length = MIN_PAYLOAD_SIZE +
                author_name.length +
                post.length;
        header.setPayloadLength(length);

        /* prepare the buffer */
        ByteBuffer blockBuffer = ByteBuffer.allocate(length);
        blockBuffer.put(author_id, 0, FIELD_UID_SIZE);
        blockBuffer.put((byte) author_name.length);
        blockBuffer.put(author_name, 0, author_name.length);
        blockBuffer.putShort((short) post.length);
        blockBuffer.put(post, 0, post.length);
        blockBuffer.putLong(chatMessage.getAuthorTimestamp());

        /* send the header, the status and the attached file */
        header.writeBlockHeader(out);
        out.write(blockBuffer.array(), 0, length);

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {

    }
}
