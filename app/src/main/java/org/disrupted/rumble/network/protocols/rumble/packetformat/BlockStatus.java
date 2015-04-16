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

import org.disrupted.rumble.database.objects.StatusMessage;
import org.disrupted.rumble.network.events.StatusReceivedEvent;
import org.disrupted.rumble.network.events.StatusSentEvent;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;

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
 * +--------+----------------------------------+
 * | Length |         Author (String)          |  1 byte + VARIABLE
 * +--------+---------+------------------------+
 * | Length |         Group (String)           |  1 byte + VARIABLE
 * +--------+---------+------------------------+
 * |      Length      |     Status (String)    |  2 bytes + VARIABLE
 * +------------------+------------------------+
 * |             Time of Creation              |
 * +                                           |  8 bytes
 * +-------------------------------------------+
 * |              Time to Live                 |
 * |                                           |  8 bytes
 * +-------------------+-----------------------+
 * |   Hop Count       |      Hop Limit        |  2 bytes + 2 bytes
 * +-------------------+-----------------------+
 * |    Replication    |    like   |              2 byte + 1 byte
 * +-------------------+-----------+
 *
 * @author Marlinski
 */
public class BlockStatus extends Block{

    private static final String TAG = "BlockStatus";

    /*
     * Byte size
     */
    private static final int AUTHOR_LENGTH_FIELD = 1;
    private static final int GROUP_LENGTH_FIELD  = 1;
    private static final int STATUS_LENGTH_FIELD = 2;
    private static final int TIME_OF_CREATION    = 8;
    private static final int TTL                 = 8;
    private static final int HOP_COUNT           = 2;
    private static final int HOP_LIMIT           = 2;
    private static final int REPLICATION         = 2;
    private static final int LIKE                = 1;

    private  static final int MIN_PAYLOAD_SIZE = (
            AUTHOR_LENGTH_FIELD +
            GROUP_LENGTH_FIELD +
            STATUS_LENGTH_FIELD +
            TIME_OF_CREATION +
            TTL +
            HOP_COUNT +
            HOP_LIMIT +
            REPLICATION +
            LIKE);

    private static final int MAX_STATUS_SIZE = 500; // limiting status to 500 character;
    private static final int MAX_BLOCK_STATUS_SIZE = MIN_PAYLOAD_SIZE + 255*2 + MAX_STATUS_SIZE;

    private StatusMessage status;

    public BlockStatus(StatusMessage status) {
        super(new BlockHeader());
        this.header.setBlockType(BlockHeader.BLOCKTYPE_STATUS);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);
        this.status = status;
    }

    public BlockStatus(BlockHeader header) {
        super(header);
        this.status = null;
    }

    @Override
    public long readBlock(LinkLayerConnection con) throws MalformedRumblePacket, IOException, InputOutputStreamException {
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_STATUS)
            throw new MalformedRumblePacket("Block type BLOCK_FILE expected");

        long readleft = header.getBlockLength();
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_BLOCK_STATUS_SIZE))
            throw new MalformedRumblePacket("wrong header length parameter: "+readleft);

        long timeToTransfer = System.currentTimeMillis();


        /* read the block */
        InputStream in = con.getInputStream();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        if (in.read(blockBuffer, 0, (int)header.getBlockLength()) < (int)header.getBlockLength())
            throw new MalformedRumblePacket("read less bytes than expected");

        /* process the read buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            short authorLength = byteBuffer.get();
            readleft -= 1;
            if ((authorLength <= 0) || (authorLength > readleft))
                throw new MalformedRumblePacket("wrong author length parameter: " + authorLength);
            byte[] author = new byte[authorLength];
            byteBuffer.get(author, 0, authorLength);
            readleft -= authorLength;

            short groupLength = byteBuffer.get();
            readleft -= 1;
            if ((groupLength <= 0) || (groupLength > readleft))
                throw new MalformedRumblePacket("wrong group length parameter: " + groupLength);
            byte[] group = new byte[groupLength];
            byteBuffer.get(group, 0, groupLength);
            readleft -= groupLength;

            short postLength = byteBuffer.getShort();
            readleft -= 2;
            if ((postLength <= 0) || (postLength > readleft))
                throw new MalformedRumblePacket("wrong status length parameter: " + postLength);
            byte[] post = new byte[postLength];
            byteBuffer.get(post, 0, postLength);

            long toc = byteBuffer.getLong();
            long ttl = byteBuffer.getLong();
            short hopCount = byteBuffer.getShort();
            short hopLimit = byteBuffer.getShort();
            short replication = byteBuffer.getShort();
            byte like = byteBuffer.get();

        /* assemble the status */
            status = new StatusMessage(new String(post), new String(author), toc);
            status.setTimeOfArrival(System.currentTimeMillis() / 1000L);
            status.setGroup(new String(group));
            status.setTimeOfCreation(toc);
            status.setHopCount((int) hopCount);
            status.setHopLimit((int) hopLimit);
            status.setTTL((int) ttl);
            status.addReplication((int) replication);
            status.setLike((int) like);

            timeToTransfer = (System.currentTimeMillis() - timeToTransfer);
            EventBus.getDefault().post(new StatusReceivedEvent(
                            status,
                            con.getRemoteLinkLayerAddress(),
                            RumbleProtocol.protocolID,
                            con.getLinkLayerIdentifier(),
                            header.getBlockLength(),
                            timeToTransfer)
            );
            status.discard();
        } catch (BufferUnderflowException exception) {
            throw new MalformedRumblePacket("buffer too small");
        }

        return header.getBlockLength();
    }

    @Override
    public long writeBlock(LinkLayerConnection con) throws IOException,InputOutputStreamException {
        long timeToTransfer = System.currentTimeMillis();

        /* calculate the total block size */
        byte[] post   = status.getPost().getBytes(Charset.forName("UTF-8"));
        byte[] group = status.getGroup().getBytes(Charset.forName("UTF-8"));
        byte[] author = status.getAuthor().getBytes(Charset.forName("UTF-8"));
        int length = MIN_PAYLOAD_SIZE +
                author.length +
                group.length +
                post.length;
        header.setBlockHeaderLength(length);

        BlockFile blockFile = null;
        if(status.hasAttachedFile()) {
            header.setLastBlock(false);
            blockFile = new BlockFile(status.getFileName(), status.getUuid());
        }

        /* prepare the buffer */
        ByteBuffer blockBuffer = ByteBuffer.allocate(length);
        blockBuffer.put((byte)author.length);
        blockBuffer.put(author,0,author.length);
        blockBuffer.put((byte)group.length);
        blockBuffer.put(group,0,group.length);
        blockBuffer.putShort((short) post.length);
        blockBuffer.put(post,0,post.length);
        blockBuffer.putLong(status.getTimeOfCreation());
        blockBuffer.putLong(status.getTTL());
        blockBuffer.putShort((short) status.getHopCount());
        blockBuffer.putShort((short) status.getHopLimit());
        blockBuffer.putShort((short) status.getReplication());
        blockBuffer.put((byte) status.getLike());

        /* send the header, the status and the attached file */
        header.writeBlock(con.getOutputStream());
        con.getOutputStream().write(blockBuffer.array(),0,length);
        if(blockFile != null)
            blockFile.writeBlock(con);

        /*
         * It is very important to post an event as it will be catch by the
         * CacheManager and will update the database accordingly
         */
        timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
        List<String> recipients = new ArrayList<String>();
        recipients.add(con.getRemoteLinkLayerAddress());
        EventBus.getDefault().post(new StatusSentEvent(
                        status,
                        recipients,
                        RumbleProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        header.getBlockLength()+header.BLOCK_HEADER_LENGTH,
                        timeToTransfer)
        );

        return header.getBlockLength()+header.BLOCK_HEADER_LENGTH;
    }

    public StatusMessage getStatus() {
        return status;
    }

    @Override
    public void dismiss() {
        if(status != null)
            this.status.discard();
    }
}

