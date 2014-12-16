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

package org.disrupted.rumble.network.protocols.rumble.packetformat;

import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.BufferMismatchBlockSize;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlock;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

/**
 * A BlockStatus holds all the information necessary to retrieve a Status
 *
 * +--------------------------+
 * |       BlockHeader        |
 * +--------------------------+
 *
 *
 * +-------------------------------------------+
 * |                                           |
 * |       UID = Hash(author, post, toc)       |   16 bytes (128 bits UID)
 * |                                           |
 * |                                           |
 * +--------+----------------------------------+
 * | Length |              Author (String)     |   1 byte + variable
 * +--------+---------+------------------------+
 * |      Length      |     Status (String)    |   2 bytes + variable
 * +------------------+------------------------+
 * |             Time of Creation              |
 * +                                           |   8 bytes
 * +-------------------------------------------+
 * |              Time to Live                 |
 * |                                           |  8 bytes
 * +-------------------+-----------------------+
 * |   Hop Count       |      Replication      |  2 bytes + 2 bytes
 * +-------------------+-----------------------+
 * |     like          |
 * +-------------------+
 *
 * @author Marlinski
 */
public class BlockStatus extends Block {

    private static final String TAG = "BlockStatus";

    /*
     * Byte size
     */
    private static final int UID                 = 16;
    private static final int AUTHOR_LENGTH_FIELD = 2;
    private static final int STATUS_LENGTH_FIELD = 2;
    private static final int TIME_OF_CREATION    = 8;
    private static final int TTL                 = 8;
    private static final int HOPS                = 2;
    private static final int REPLICATION         = 2;
    private static final int LIKE                = 2;

    public  static final int MIN_PAYLOAD_SIZE = ( UID +
            AUTHOR_LENGTH_FIELD +
            STATUS_LENGTH_FIELD +
            TIME_OF_CREATION +
            TTL +
            HOPS +
            REPLICATION +
            LIKE);

    public static final int SUBTYPE = 0x02;

    private StatusMessage status;

    public BlockStatus(BlockHeader header, StatusMessage status) {
        super(header, null);
        this.header.setType(BlockHeader.Type.PUSH);
        this.header.setSubtype(SUBTYPE);
        this.header.setLastBlock(true);
        this.status = status;
    }

    public BlockStatus(BlockHeader header, byte[] payload) {
        super(header, payload);
        status = null;
    }

    @Override
    public void readBuffer() throws BufferMismatchBlockSize, MalformedRumblePacket {
        if(payload.length < MIN_PAYLOAD_SIZE)
            throw new BufferMismatchBlockSize();

        ByteBuffer byteBuffer = ByteBuffer.wrap(payload);

        byte[] uid = new byte[UID];
        byteBuffer.get(uid,0,UID);

        short authorLength = byteBuffer.getShort();
        byte[] author;
        if((authorLength > 0) && (authorLength < (payload.length-UID)))
            author = new byte[authorLength];
        else
            throw new MalformedRumblePacket("wrong author length parameter: "+authorLength);
        byteBuffer.get(author);

        short postLength = byteBuffer.getShort();
        byte[] post;
        if((postLength > 0) && (postLength < (payload.length - UID - authorLength)))
            post = new byte[postLength];
        else
            throw new MalformedRumblePacket("wrong post length parameter: "+postLength);

        byteBuffer.get(post);

        long  toc   = byteBuffer.getLong();
        long  ttl   = byteBuffer.getLong();
        short hops  = byteBuffer.getShort();
        short replication = byteBuffer.getShort();
        short like  = byteBuffer.getShort();

        status = new StatusMessage(new String(post), new String(author), toc);
        status.setUuid(new String(uid));
        status.setTimeOfCreation(toc);
        status.setHopCount((int) hops);
        status.setTTL((int) ttl);
        status.addReplication((int) replication);
        status.setLike((int) like);
        status.setTimeOfArrival(System.currentTimeMillis()/1000L);
    }

    @Override
    public StatusMessage getMessage() throws MalformedBlock {
        return status;
    }

    @Override
    public byte[] getBytes() throws MalformedRumblePacket {
        byte[] post   = status.getPost().getBytes(Charset.forName("UTF-8"));
        byte[] author = status.getAuthor().getBytes(Charset.forName("UTF-8"));

        int length = MIN_PAYLOAD_SIZE +
                author.length +
                post.length;

        header.setPayloadLength(length);

        ByteBuffer byteBuffer = ByteBuffer.allocate(length+header.HEADER_LENGTH);

        /*
         * we first add the header
         */
        byteBuffer.put(header.getBytes());

        /*
         * and then the payload
         */
        byteBuffer.put(status.getUuid().getBytes(),0,UID);

        byteBuffer.putShort((short) author.length);
        byteBuffer.put(author);

        byteBuffer.putShort((short) post.length);
        byteBuffer.put(post);

        byteBuffer.putLong(status.getTimeOfCreation());
        byteBuffer.putLong(status.getTTL());

        byteBuffer.putShort((short)status.getHopCount());
        byteBuffer.putShort((short)status.getReplication());
        byteBuffer.putShort((short)status.getLike());

        return byteBuffer.array();
    }

}

