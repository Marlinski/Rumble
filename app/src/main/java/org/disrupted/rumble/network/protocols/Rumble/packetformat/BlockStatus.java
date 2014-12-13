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

package org.disrupted.rumble.network.protocols.Rumble.packetformat;

import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.BufferMismatchBlockSize;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.MalformedBlock;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.MalformedRumblePacket;

import java.nio.ByteBuffer;
import java.util.Arrays;

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

    private static final int UID = 16;
    private static final int AUTHOR_LENGTH_FIELD = 1;
    private static final int STATUS_LENGTH_FIELD = 2;
    private static final int TIME_OF_CREATION    = 8;
    private static final int SCORE               = 2;
    private static final int HOPS = 1;
    private static final int TTL = 1;

    public  static final int MIN_HEADER_LENGTH = ( UID +
            AUTHOR_LENGTH_FIELD +
            STATUS_LENGTH_FIELD +
            TIME_OF_CREATION +
            SCORE +
            HOPS +
            TTL);

    public static final int SUBTYPE = 0x02;

    private StatusMessage status;

    public BlockStatus(BlockHeader header, StatusMessage status) {
        super(header, null);
    }

    public BlockStatus(BlockHeader header, byte[] payload) {
        super(header, payload);
    }

    @Override
    public void readBuffer() throws BufferMismatchBlockSize, MalformedRumblePacket {

        if(payload.length < MIN_HEADER_LENGTH)
            throw new BufferMismatchBlockSize();

        ByteBuffer byteBuffer = ByteBuffer.wrap(payload);

        byte[] uid = new byte[UID];
        byteBuffer.get(uid,0,UID);

        short authorLength = byteBuffer.getShort();
        byte[] author = new byte[authorLength];
        byteBuffer.get(author,byteBuffer.position(),authorLength);

        short postLength = byteBuffer.getShort();
        byte[] post = new byte[authorLength];
        byteBuffer.get(post,byteBuffer.position(),postLength);

        long  toc   = byteBuffer.getLong();
        short score = byteBuffer.getShort();
        byte  hop   = byteBuffer.get();
        byte  ttl   = byteBuffer.get();

        status = new StatusMessage(new String(author), new String(post), toc);
        status.setUuid(new String(uid));
        status.setTimeOfCreation(toc);
        status.setHopCount((int)hop);
        status.setTTL((int)ttl);
        status.setScore((int)score);
        status.setTimeOfArrival(System.currentTimeMillis()/1000L);
    }

    @Override
    public StatusMessage getMessage() throws MalformedBlock {
        return status;
    }

    @Override
    public byte[] getBytes() throws MalformedRumblePacket {
        int length = MIN_HEADER_LENGTH+status.getAuthor().length()+status.getPost().length();
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);

        byteBuffer.put(status.getUuid().getBytes(),0,UID);

        byteBuffer.put((byte)status.getAuthor().length());
        byteBuffer.put(status.getAuthor().getBytes());

        byteBuffer.putShort((short)status.getPost().length());
        byteBuffer.put(status.getPost().getBytes());

        byteBuffer.putLong(status.getTimeOfCreation());

        byteBuffer.putShort(status.getScore().shortValue());

        byteBuffer.put(status.getHopCount().byteValue());
        byteBuffer.put(status.getHopCount().byteValue());

        return byteBuffer.array();
    }

}

