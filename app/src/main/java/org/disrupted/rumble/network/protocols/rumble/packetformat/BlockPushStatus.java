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
import android.util.Log;

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
import org.disrupted.rumble.util.AESUtil;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;

import de.greenrobot.event.EventBus;

/**
 * A BlockStatus holds all the information necessary to retrieve a Status
 *
 * +-------------------------------------------+
 * |               Group ID                    |  8 byte  Group UID
 * +-------------------------------------------+
 * |            Initialization                 |  256 bits IV for AES
 * |                 Vector                    |  (0 if unencrypted)
 * +-------------------------------------------+ +++++++++++++++++++ BEGIN ENCRYPTED BLOCK
 * |            Sender User ID                 |  8 byte Sender UID
 * +-------------------------------------------+
 * |            Author User ID                 |  8 byte Author UID
 * +--------+----------------------------------+
 * | Length |         Author (String)          |  1 byte + VARIABLE
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
 * +-------------------------------------------+ +++++++++++++++++++ END ENCRYPTED BLOCK
 *
 * @author Marlinski
 */
public class BlockPushStatus extends Block{

    private static final String TAG = "BlockStatus";

    /*
     * Byte size
     */
    private static final int FIELD_GROUP_GID_SIZE       = Group.GROUP_GID_RAW_SIZE;
    private static final int FIELD_AES_IV_SIZE          = AESUtil.IVSIZE;
    private static final int FIELD_SENDER_UID_SIZE      = Contact.CONTACT_UID_RAW_SIZE;
    private static final int FIELD_AUTHOR_UID_SIZE      = Contact.CONTACT_UID_RAW_SIZE;
    private static final int FIELD_AUTHOR_LENGTH_SIZE   = 1;
    private static final int FIELD_STATUS_LENGTH_SIZE   = 2;
    private static final int FIELD_FILENAME_LENGTH_SIZE = 1;
    private static final int FIELD_TOC_SIZE             = 8;
    private static final int FIELD_TTL_SIZE             = 8;
    private static final int FIELD_HOPCOUNT_SIZE        = 2;
    private static final int FIELD_HOPLIMIT_SIZE        = 2;
    private static final int FIELD_REPLICATION_SIZE     = 2;
    private static final int FIELD_LIKE_SIZE            = 1;


    private  static final int ENCRYPTED_BLOCK_HEADER = (
            FIELD_GROUP_GID_SIZE +
            FIELD_AES_IV_SIZE);

    private  static final int MIN_ENCRYPTED_BLOCK_SIZE = (
            FIELD_SENDER_UID_SIZE +
            FIELD_AUTHOR_UID_SIZE +
            FIELD_AUTHOR_LENGTH_SIZE +
            FIELD_STATUS_LENGTH_SIZE +
            FIELD_FILENAME_LENGTH_SIZE +
            FIELD_TOC_SIZE +
            FIELD_TTL_SIZE +
            FIELD_HOPCOUNT_SIZE +
            FIELD_HOPLIMIT_SIZE +
            FIELD_REPLICATION_SIZE +
            FIELD_LIKE_SIZE);

    private  static final int MIN_PAYLOAD_SIZE = (
            ENCRYPTED_BLOCK_HEADER +
            MIN_ENCRYPTED_BLOCK_SIZE);

    private static final int MAX_BLOCK_STATUS_SIZE = MIN_PAYLOAD_SIZE +
            Contact.CONTACT_NAME_MAX_SIZE +
            PushStatus.STATUS_POST_MAX_SIZE +
            PushStatus.STATUS_ATTACHED_FILE_MAX_SIZE;

    private PushStatus   status;

    public BlockPushStatus(CommandSendPushStatus command) {
        super(new BlockHeader());
        this.header.setBlockType(BlockHeader.BLOCKTYPE_PUSH_STATUS);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);
        this.status = command.getStatus();
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

        /* read the entire block into a block buffer */
        InputStream in = con.getInputStream();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        int count=in.read(blockBuffer, 0, (int)header.getBlockLength());
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < (int)header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected", count);

        /* process the block buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            byte[] group_id = new byte[FIELD_GROUP_GID_SIZE];
            byteBuffer.get(group_id, 0, FIELD_GROUP_GID_SIZE);
            readleft -= FIELD_GROUP_GID_SIZE;

            String group_id_base64  = Base64.encodeToString(group_id, 0, FIELD_GROUP_GID_SIZE, Base64.NO_WRAP);
            Group group = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext())
                    .getGroup(group_id_base64);
            if(group == null)
                return header.getBlockLength(); // we do not belong to the group

            byte[] iv = new byte[FIELD_AES_IV_SIZE];
            byteBuffer.get(iv, 0, FIELD_AES_IV_SIZE);
            readleft -= FIELD_AES_IV_SIZE;
            int sum = 0;
            for (byte b : iv) {sum |= b;}

            if(sum != 0) {
                // we have an encrypted block, let's decrypt it and process the decrypted block.
                byte[] encryptedBlockBuffer = new byte[(int)readleft];
                byteBuffer.get(encryptedBlockBuffer,0,(int)readleft);

                // if the group is public, it shouldn't be encrypted.
                // we trash this status and pretend like nothing happened
                if(group.getGroupKey() == null)
                    return header.getBlockLength();

                try {
                    blockBuffer = AESUtil.decryptBlock(encryptedBlockBuffer, group.getGroupKey(), iv);
                    byteBuffer = ByteBuffer.wrap(blockBuffer);
                    readleft = blockBuffer.length;
                } catch(Exception e){
                    e.printStackTrace();
                    throw new MalformedBlockPayload("Unable to decrypt the message", 0);
                }
            } else {
                // block is not encrypted, we do nothing
            }

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

            Contact contact_tmp  = new Contact(new String(author_name),author_id_base64,false);
            status = new PushStatus(contact_tmp, group, new String(post), toc, sender_id_base64);

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
                    BlockHeader header = BlockHeader.readBlockHeader(in);
                    if(header.getBlockType() != BlockHeader.BLOCKTYPE_FILE)
                        throw new MalformedBlockPayload("FileBlock Header expected", readleft);
                    BlockFile block = new BlockFile(header);
                    block.setEncryptionKey(group.getGroupKey());
                    block.readBlock(channel);
                    channel.bytes_received += header.getBlockLength();
                    tempfile = block.filename;
                } catch (MalformedBlockHeader e) {
                    throw new MalformedBlockPayload("FileBlock Header expected", readleft);
                }
            }

            timeToTransfer = (System.nanoTime() - timeToTransfer);
            channel.status_received++;
            channel.bytes_received += header.getBlockLength();
            channel.in_transmission_time += timeToTransfer;

            if(status.hasAttachedFile() && tempfile.equals("")) {
                // there was a problem with the attached file
                status.discard();
                return header.getBlockLength();
            } else {
                EventBus.getDefault().post(new PushStatusReceived(
                                status,
                                sender_id_base64,
                                tempfile,
                                RumbleProtocol.protocolID,
                                con.getLinkLayerIdentifier(),
                                header.getBlockLength(),
                                timeToTransfer)
                );
                return header.getBlockLength();
            }
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", header.getBlockLength() - readleft);
        }
    }

    @Override
    public long writeBlock(ProtocolChannel channel) throws IOException,InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        long timeToTransfer = System.nanoTime();

        /* calculate the encrypted block size */
        byte[] sender_id   = Base64.decode(DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                .getLocalContact().getUid(), Base64.NO_WRAP);
        byte[] author_id   = Base64.decode(status.getAuthor().getUid(), Base64.NO_WRAP);
        byte[] author_name = status.getAuthor().getName().getBytes(Charset.forName("UTF-8"));
        byte[] post     = status.getPost().getBytes(Charset.forName("UTF-8"));
        byte[] filename = status.getFileName().getBytes(Charset.forName("UTF-8"));

        int encrypted_block_size = MIN_ENCRYPTED_BLOCK_SIZE +
                author_name.length +
                post.length +
                filename.length;

        /* prepare the encrypted buffer */
        ByteBuffer toEncryptBuffer = ByteBuffer.allocate(encrypted_block_size);
        toEncryptBuffer.put(sender_id, 0, FIELD_SENDER_UID_SIZE);
        toEncryptBuffer.put(author_id, 0, FIELD_AUTHOR_UID_SIZE);
        toEncryptBuffer.put((byte)author_name.length);
        toEncryptBuffer.put(author_name, 0, author_name.length);
        toEncryptBuffer.putShort((short) post.length);
        toEncryptBuffer.put(post, 0, post.length);
        toEncryptBuffer.put((byte) filename.length);
        toEncryptBuffer.put(filename, 0, filename.length);
        toEncryptBuffer.putLong(status.getTimeOfCreation());
        toEncryptBuffer.putLong(status.getTTL());
        toEncryptBuffer.putShort((short) status.getHopCount());
        toEncryptBuffer.putShort((short) status.getHopLimit());
        toEncryptBuffer.putShort((short) status.getReplication());
        toEncryptBuffer.put((byte) status.getLike());

        /* encrypt the buffer (if necessary) */
        byte[] iv;
        byte[] encryptedBuffer;
        if(status.getGroup().isIsprivate()) {
            try {
                iv = AESUtil.generateRandomIV();
                encryptedBuffer = AESUtil.encryptBlock(toEncryptBuffer.array(), status.getGroup().getGroupKey(), iv);
            } catch(Exception e) {
                e.printStackTrace();
                return 0;
            }
        } else {
            iv = new byte[FIELD_AES_IV_SIZE];
            Arrays.fill(iv, (byte) 0);
            encryptedBuffer = toEncryptBuffer.array();
        }

        /* compute final block size */
        byte[] group_id = Base64.decode(status.getGroup().getGid(), Base64.NO_WRAP);
        int length = ENCRYPTED_BLOCK_HEADER + encryptedBuffer.length;

        /* fill the buffer */
        ByteBuffer blockBuffer = ByteBuffer.allocate(length);
        blockBuffer.put(group_id, 0, FIELD_GROUP_GID_SIZE);
        blockBuffer.put(iv, 0, FIELD_AES_IV_SIZE);
        blockBuffer.put(encryptedBuffer, 0, encryptedBuffer.length);

        /* send the header, the status and the attached file */
        if(status.hasAttachedFile())
            header.setLastBlock(false);
        header.setPayloadLength(length);
        header.writeBlockHeader(con.getOutputStream());
        con.getOutputStream().write(blockBuffer.array(),0,length);
        if(status.hasAttachedFile()) {
            File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), status.getFileName());
            if(attachedFile.exists() && attachedFile.isFile()) {
                BlockFile blockFile = new BlockFile(status.getFileName(), status.getUuid());
                blockFile.setEncryptionKey(status.getGroup().getGroupKey());
                blockFile.writeBlock(channel);
            }
        }

        timeToTransfer = (System.nanoTime() - timeToTransfer);
        channel.status_sent++;
        channel.bytes_sent+=header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
        channel.out_transmission_time += timeToTransfer;

        EventBus.getDefault().post(new PushStatusSent(
                        status,
                        channel.getRecipientList(),
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

