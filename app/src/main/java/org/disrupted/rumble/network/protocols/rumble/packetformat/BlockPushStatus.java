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

import android.util.Base64;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.PushStatusReceived;
import org.disrupted.rumble.network.protocols.events.PushStatusSent;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockHeader;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * A BlockStatus holds all the information necessary to retrieve a Status
 *
 * +-------------------------------------------+
 * |               User ID                     |  8 byte Sender UID
 * +-------------------------------------------+
 * |               User ID                     |  8 byte Author UID
 * +--------+----------------------------------+
 * | Length |         Author (String)          |  1 byte + VARIABLE
 * +-------------------------------------------+
 * |               Group ID                    |  8 byte  Group UID
 * +--------+---------+------------------------+
 * | Length |         Group (String)           |  1 byte + VARIABLE
 * +--------+---------+------------------------+
 * |      Length      |     Status (String)    |  2 bytes + VARIABLE
 * +--------+---------+------------------------+
 * | Length |           FileName               |  1 byte + VARIABLE
 * +--------+---------+------------------------+
 * |             Time of Creation              |  8 bytes
 * +-------------------------------------------+
 * |              Time to Live                 |  8 bytes
 * +-------------------+-----------------------+
 * |   Hop Count       |      Hop Limit        |  2 bytes + 2 bytes
 * +-------------------+-----------------------+
 * |    Replication    |    like   |              2 byte + 1 byte
 * +-------------------+-----------+
 *
 * @author Marlinski
 */
public class BlockPushStatus extends Block{

    private static final String TAG = "BlockStatus";

    /*
     * Byte size
     */
    private static final int FIELD_SENDER_UID_SIZE      = Contact.CONTACT_UID_RAW_SIZE;
    private static final int FIELD_AUTHOR_UID_SIZE      = Contact.CONTACT_UID_RAW_SIZE;
    private static final int FIELD_AUTHOR_LENGTH_SIZE   = 1;
    private static final int FIELD_GID_SIZE             = Group.GROUP_GID_RAW_SIZE;
    private static final int FIELD_GROUP_LENGTH_SIZE    = 1;
    private static final int FIELD_STATUS_LENGTH_SIZE   = 2;
    private static final int FIELD_FILENAME_LENGTH_SIZE = 1;
    private static final int FIELD_TOC_SIZE             = 8;
    private static final int FIELD_TTL_SIZE             = 8;
    private static final int FIELD_HOPCOUNT_SIZE        = 2;
    private static final int FIELD_HOPLIMIT_SIZE        = 2;
    private static final int FIELD_REPLICATION_SIZE     = 2;
    private static final int FIELD_LIKE_SIZE            = 1;

    private  static final int MIN_PAYLOAD_SIZE = (
                    FIELD_SENDER_UID_SIZE +
                    FIELD_AUTHOR_UID_SIZE +
                    FIELD_AUTHOR_LENGTH_SIZE +
                    FIELD_GID_SIZE +
                    FIELD_GROUP_LENGTH_SIZE +
                    FIELD_STATUS_LENGTH_SIZE +
                    FIELD_FILENAME_LENGTH_SIZE +
                    FIELD_TOC_SIZE +
                    FIELD_TTL_SIZE +
                    FIELD_HOPCOUNT_SIZE +
                    FIELD_HOPLIMIT_SIZE +
                    FIELD_REPLICATION_SIZE +
                    FIELD_LIKE_SIZE);

    private static final int MAX_BLOCK_STATUS_SIZE = MIN_PAYLOAD_SIZE +
            Contact.CONTACT_NAME_MAX_SIZE +
            Group.GROUP_NAME_MAX_SIZE +
            PushStatus.STATUS_POST_MAX_SIZE +
            PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE;

    private PushStatus   status;
    private Set<Contact> recipientList;

    public BlockPushStatus(CommandSendPushStatus command) {
        super(new BlockHeader());
        this.header.setBlockType(BlockHeader.BLOCKTYPE_PUSH_STATUS);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);
        this.status = command.getStatus();
        this.recipientList = command.getRecipientList();
    }

    public BlockPushStatus(BlockHeader header) {
        super(header);
        this.status = null;
    }

    @Override
    public long readBlock(ProtocolChannel channel) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_PUSH_STATUS)
            throw new MalformedBlockPayload("Block type BLOCK_STATUS expected", 0);

        long readleft = header.getBlockLength();
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_BLOCK_STATUS_SIZE))
            throw new MalformedBlockPayload("wrong header length parameter: "+readleft, 0);

        long timeToTransfer = System.nanoTime();


        /* read the block */
        InputStream in = con.getInputStream();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        int count=in.read(blockBuffer, 0, (int)header.getBlockLength());
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < (int)header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected", count);

        /* process the read buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            byte[] sender_id = new byte[FIELD_SENDER_UID_SIZE];
            byteBuffer.get(sender_id, 0, FIELD_SENDER_UID_SIZE);
            readleft -= FIELD_SENDER_UID_SIZE;

            byte[] author_id = new byte[FIELD_AUTHOR_UID_SIZE];
            byteBuffer.get(author_id, 0, FIELD_AUTHOR_UID_SIZE);
            readleft -= FIELD_AUTHOR_UID_SIZE;

            short authorLength = byteBuffer.get();
            readleft -= FIELD_AUTHOR_LENGTH_SIZE;
            if ((authorLength <= 0) || (authorLength > readleft) || (authorLength > Contact.CONTACT_NAME_MAX_SIZE))
                throw new MalformedBlockPayload("wrong author.length parameter: " + authorLength, header.getBlockLength()-readleft);
            byte[] author_name = new byte[authorLength];
            byteBuffer.get(author_name, 0, authorLength);
            readleft -= authorLength;

            byte[] group_id = new byte[FIELD_GID_SIZE];
            byteBuffer.get(group_id, 0, FIELD_GID_SIZE);
            readleft -= FIELD_GID_SIZE;

            short groupLength = byteBuffer.get();
            readleft -= FIELD_GROUP_LENGTH_SIZE;
            if ((groupLength <= 0) || (groupLength > readleft) || (groupLength > Group.GROUP_NAME_MAX_SIZE))
                throw new MalformedBlockPayload("wrong group.length parameter: " + groupLength, header.getBlockLength()-readleft);
            byte[] group_name = new byte[groupLength];
            byteBuffer.get(group_name, 0, groupLength);
            readleft -= groupLength;

            short postLength = byteBuffer.getShort();
            readleft -= FIELD_STATUS_LENGTH_SIZE;
            if ((postLength <= 0) || (postLength > readleft) || (postLength > PushStatus.STATUS_POST_MAX_SIZE))
                throw new MalformedBlockPayload("wrong status.length parameter: " + postLength, header.getBlockLength()-readleft);
            byte[] post = new byte[postLength];
            byteBuffer.get(post, 0, postLength);
            readleft -= postLength;

            short filenameLength = byteBuffer.get();
            readleft -= FIELD_FILENAME_LENGTH_SIZE;
            if ((filenameLength < 0) || (filenameLength > readleft) || (filenameLength > PushStatus.STATUS_FILENAME_MAX_SIZE))
                throw new MalformedBlockPayload("wrong filename.length parameter: " + filenameLength, header.getBlockLength()-readleft);
            byte[] filename = new byte[filenameLength];
            byteBuffer.get(filename, 0, filenameLength);
            readleft -= filenameLength;

            long toc = byteBuffer.getLong();
            readleft -= FIELD_TOC_SIZE;

            long ttl = byteBuffer.getLong();
            readleft -= FIELD_TTL_SIZE;

            short hopCount = byteBuffer.getShort();
            readleft -= FIELD_HOPCOUNT_SIZE;

            short hopLimit = byteBuffer.getShort();
            readleft -= FIELD_HOPLIMIT_SIZE;

            short replication = byteBuffer.getShort();
            readleft -= FIELD_REPLICATION_SIZE;

            byte like = byteBuffer.get();
            readleft -= FIELD_LIKE_SIZE;

            if(readleft > 0)
                throw new MalformedBlockPayload("wrong header.length parameter, no more data to read: " + (header.getBlockLength()-readleft), header.getBlockLength()-readleft);

            /* assemble the status */
            String sender_id_base64 = Base64.encodeToString(sender_id,0, FIELD_AUTHOR_UID_SIZE,Base64.NO_WRAP);
            String author_id_base64 = Base64.encodeToString(author_id,0, FIELD_AUTHOR_UID_SIZE,Base64.NO_WRAP);
            String group_id_base64  = Base64.encodeToString(group_id,0, FIELD_GID_SIZE,Base64.NO_WRAP);

            Contact contact_tmp  = new Contact(new String(author_name),author_id_base64,false);
            Group   group_tmp = new Group(new String(group_name), group_id_base64, null);
            status = new PushStatus(contact_tmp, group_tmp, new String(post), toc, sender_id_base64);

            status.setFileName(new String(filename));
            status.setTimeOfArrival(System.currentTimeMillis());
            status.setTimeOfCreation(toc);
            status.setHopCount((int) hopCount);
            status.setHopLimit((int) hopLimit);
            status.setTTL((int) ttl);
            status.addReplication((int) replication);
            status.setLike((int) like);

            String tempfile = "";
            if(status.hasAttachedFile()) {
                try {
                    BlockHeader header = BlockHeader.readBlock(in);
                    if(header.getBlockType() != BlockHeader.BLOCKTYPE_FILE)
                        throw new MalformedBlockPayload("FileBlock Header expected", readleft);
                    BlockFile block = new BlockFile(header);
                    block.readBlock(channel);
                    tempfile = block.filename;
                } catch (MalformedBlockHeader e) {
                    throw new MalformedBlockPayload("FileBlock Header expected", readleft);
                }
            }

            timeToTransfer = (System.nanoTime() - timeToTransfer);
            EventBus.getDefault().post(new PushStatusReceived(
                            status,
                            sender_id_base64,
                            tempfile,
                            RumbleProtocol.protocolID,
                            con.getLinkLayerIdentifier(),
                            header.getBlockLength(),
                            timeToTransfer)
            );
            status.discard();

            return header.getBlockLength();
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", header.getBlockLength() - readleft);
        }
    }

    @Override
    public long writeBlock(ProtocolChannel channel) throws IOException,InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        long timeToTransfer = System.nanoTime();

        /* calculate the total block size */
        byte[] post     = status.getPost().getBytes(Charset.forName("UTF-8"));
        byte[] filename = status.getFileName().getBytes(Charset.forName("UTF-8"));
        byte[] group_name  = status.getGroup().getName().getBytes(Charset.forName("UTF-8"));
        byte[] group_id    = Base64.decode(status.getGroup().getGid(), Base64.NO_WRAP);
        byte[] author_name = status.getAuthor().getName().getBytes(Charset.forName("UTF-8"));
        byte[] author_id   = Base64.decode(status.getAuthor().getUid(), Base64.NO_WRAP);
        byte[] sender_id   = Base64.decode(DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                .getLocalContact().getUid(),Base64.NO_WRAP);

        int length = MIN_PAYLOAD_SIZE +
                author_name.length +
                group_name.length +
                post.length +
                filename.length;
        header.setPayloadLength(length);

        BlockFile blockFile = null;
        if(status.hasAttachedFile()) {
            File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), status.getFileName());
            if(attachedFile.exists() && attachedFile.isFile()) {
                header.setLastBlock(false);
                blockFile = new BlockFile(status.getFileName(), status.getUuid());
            }
        }

        /* prepare the buffer */
        ByteBuffer blockBuffer = ByteBuffer.allocate(length);
        blockBuffer.put(sender_id, 0, FIELD_SENDER_UID_SIZE);
        blockBuffer.put(author_id, 0, FIELD_AUTHOR_UID_SIZE);
        blockBuffer.put((byte)author_name.length);
        blockBuffer.put(author_name, 0, author_name.length);
        blockBuffer.put(group_id, 0, FIELD_GID_SIZE);
        blockBuffer.put((byte)group_name.length);
        blockBuffer.put(group_name, 0, group_name.length);
        blockBuffer.putShort((short) post.length);
        blockBuffer.put(post, 0, post.length);
        blockBuffer.put((byte) filename.length);
        blockBuffer.put(filename, 0, filename.length);
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
            blockFile.writeBlock(channel);

        timeToTransfer = (System.nanoTime() - timeToTransfer);
        EventBus.getDefault().post(new PushStatusSent(
                        status,
                        recipientList,
                        RumbleProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH,
                        timeToTransfer)
        );

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    public PushStatus getStatus() {
        return status;
    }

    @Override
    public void dismiss() {
        if(status != null)
            this.status.discard();
    }
}

