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
import android.util.Log;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.events.ContactInformationSent;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.HashUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.greenrobot.event.EventBus;

/**
 * A BlockContact contains information about a given user
 *
 * - Its User ID
 * - Its User Name (human readable)
 * - The list of groups ID (GID) it belongs to
 * - its interests hashtags subscription list (value, hashtag)
 * - its public key
 *
 * All those fields are not necessary at every transmission so we use a (TYPE, VALUE)
 * approach
 *
 * +-------------------------------------------+
 * |               User ID                     |   8 byte Author UID
 * +--------+----------------------------------+
 * | length |         User Name                |   1 byte + user name
 * +--------+--------+-------------------------+
 * |  TYPE  | Length |       DATA              |   1 byte + 1 byte + VARIABLE (See type)
 * +--------+--------+-------------------------+
 * |  TYPE  | Length |       DATA              |   1 byte + 1 byte + VARIABLE (See type)
 * +--------+--------+-------------------------+
 * |  TYPE  | Length |       DATA              |   1 byte + 1 byte + VARIABLE (See type)
 * +--------+--------+-------------------------+
 *               [ ... ]
 *
 *        ========================
 *
 * Data structures according to the TYPE value:
 *
 * ENTRY TYPE GROUP:
 * +-------------------------------------------+
 * |               Group ID                    |   8 byte Group GID
 * +-------------------------------------------+
 *
 * ENTRY TYPE TAG INTEREST
 * +----------+-----------+--------------------+
 * | Interest | length    |    hashtag         |  1 byte + 1 byte + hashtag
 * +----------+-----------+--------------------+
 *
 * ENTRY TYPE PUBLIC KEY
 * +----------+--------------------------------+
 * | KEY_TYPE |            KEY                 |
 * +----------+--------------------------------+
 *
 * @author Marlinski
 */
public class BlockContact extends Block {

    private static final String TAG = "BlockContact";

    /*
     * Byte size
     */
    private static final int FIELD_UID_SIZE            = HashUtil.USER_ID_SIZE;
    private static final int FIELD_AUTHOR_LENGTH_SIZE  = 1;

    private static final int MIN_PAYLOAD_SIZE       = (
              FIELD_UID_SIZE
            + FIELD_AUTHOR_LENGTH_SIZE
    );
    private static final int MAX_BLOCK_CONTACT_SIZE =  2048;

    private Contact contact;
    private int     flags;

    public BlockContact(BlockHeader header) {
        super(header);
    }

    public BlockContact(CommandSendLocalInformation command) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_CONTACT);
        this.contact = command.getContact();
        this.flags   = command.getFlags();
    }

    @Override
    public long readBlock(ProtocolChannel channel) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_CONTACT)
            throw new MalformedBlockPayload("Block type BLOCK_FILE expected",0);

        long readleft = header.getBlockLength();
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_BLOCK_CONTACT_SIZE))
            throw new MalformedBlockPayload("header.length is too short: "+readleft, 0);

        /* read the block */
        long timeToTransfer = System.currentTimeMillis();

        InputStream in = con.getInputStream();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        int count = in.read(blockBuffer, 0, (int)header.getBlockLength());
        if (count <  (int)header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected", count);

        /* process the read buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            byte[] uid = new byte[FIELD_UID_SIZE];
            byteBuffer.get(uid,0,FIELD_UID_SIZE);
            readleft -= FIELD_UID_SIZE;

            String user_id_base64 = Base64.encodeToString(uid, 0, FIELD_UID_SIZE,Base64.NO_WRAP);
            byte author_name_length = byteBuffer.get();
            readleft -= FIELD_AUTHOR_LENGTH_SIZE;

            byte[] author_name = new byte[author_name_length];
            byteBuffer.get(author_name,0,author_name_length);
            readleft -= author_name_length;

            Contact tempcontact = new Contact(new String(author_name),user_id_base64,false);

            while(readleft > 0) {
                int entryType = byteBuffer.get();          // FIELD_TYPE_SIZE
                int entrySize = (byteBuffer.get() & 0xff); // FIELD_LENGTH_SIZE
                Entry entry;
                switch (entryType) {
                    case Entry.ENTRY_TYPE_GROUP:
                        entry = new GroupEntry(entrySize);
                        entry.read(byteBuffer);
                        tempcontact.addGroup(((GroupEntry)entry).group_id_base64);
                        this.flags |= Contact.FLAG_GROUP_LIST;
                        break;
                    case Entry.ENTRY_TYPE_TAG:
                        entry = new TagInterestEntry(entrySize);
                        entry.read(byteBuffer);
                        tempcontact.addTagInterest(((TagInterestEntry)entry).hashtag, ((TagInterestEntry)entry).levelOfInterest);
                        this.flags |= Contact.FLAG_TAG_INTEREST;
                        break;
                    default:
                        entry = new NullEntry(entrySize);
                        entry.read(byteBuffer);
                        break;
                }
                readleft -= (Entry.HEADER_SIZE+entry.getEntrySize());
            }

            timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
            EventBus.getDefault().post(new ContactInformationReceived(
                            tempcontact,
                            flags,
                            channel,
                            con.getLinkLayerNeighbour(),
                            BlockHeader.BLOCK_HEADER_LENGTH + header.getBlockLength(),
                            timeToTransfer)
            );

            return header.getBlockLength();

        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", count);
        }

    }

    @Override
    public long writeBlock(ProtocolChannel channel) throws IOException, InputOutputStreamException {
        UnicastConnection con = (UnicastConnection)channel.getLinkLayerConnection();
        ArrayList<Entry> entries = new ArrayList<Entry>();

        /* prepare the entries */
        /* calculate the total block size */
        byte[] author_id   = Base64.decode(contact.getUid(),Base64.NO_WRAP);
        byte[] author_name = contact.getName().getBytes(Charset.forName("UTF-8"));

        int buffersize = MIN_PAYLOAD_SIZE;
        buffersize += author_name.length;

        if((flags & Contact.FLAG_TAG_INTEREST) == Contact.FLAG_TAG_INTEREST) {
            for (Map.Entry<String, Integer> entry : contact.getHashtagInterests().entrySet()) {
                TagInterestEntry bufferEntry = new TagInterestEntry(entry.getKey(), entry.getValue().byteValue());
                entries.add(bufferEntry);
                buffersize += bufferEntry.getEntrySize() + Entry.HEADER_SIZE;
            }
        }
        if((flags & Contact.FLAG_GROUP_LIST) == Contact.FLAG_GROUP_LIST) {
            for (String gid : contact.getJoinedGroupIDs()) {
                GroupEntry bufferEntry = new GroupEntry(gid);
                entries.add(bufferEntry);
                buffersize += bufferEntry.getEntrySize() + Entry.HEADER_SIZE;
            }
        }
        header.setPayloadLength(buffersize);

        /* prepare the block payload buffer */
        byte[] buffer = new byte[buffersize];
        ByteBuffer blockBuffer = ByteBuffer.wrap(buffer);

        blockBuffer.put(author_id, 0, FIELD_UID_SIZE);
        blockBuffer.put((byte)author_name.length);
        blockBuffer.put(author_name, 0, author_name.length);

        for( Entry entry : entries ) {
            try {
                entry.write(blockBuffer);
            } catch (BufferOverflowException e) {
                Log.d(TAG, "BufferOverFlow");
            } catch (ReadOnlyBufferException e) {
                Log.d(TAG, "ReadOnlyBufferException");
            }
        }

        long timeToTransfer = System.currentTimeMillis();

        /* send the BlockHeader and the BlockPayload */
        header.writeBlock(con.getOutputStream());
        con.getOutputStream().write(blockBuffer.array(), 0, buffersize);

        timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
        EventBus.getDefault().post(new ContactInformationSent(
                        contact,
                        channel,
                        BlockHeader.BLOCK_HEADER_LENGTH + header.getBlockLength(),
                        timeToTransfer)
        );

        return BlockHeader.BLOCK_HEADER_LENGTH + header.getBlockLength();
    }

    @Override
    public void dismiss() {

    }

    /*
     * Utility class for Writing / Reading entries
     */
    private abstract class Entry {

        /* entry header */
        public static final int FIELD_TYPE_SIZE    = 1;
        public static final int FIELD_LENGTH_SIZE  = 1;
        public static final int HEADER_SIZE = FIELD_TYPE_SIZE + FIELD_LENGTH_SIZE;

        /* Type Field values */
        public static final int ENTRY_TYPE_GROUP   = 0x01;
        public static final int ENTRY_TYPE_TAG     = 0x02;
        public static final int ENTRY_TYPE_PUB_KEY = 0x03;

        /* Entry payload size (without EntryHeader) */
        int entrySize;

        public Entry(int entrySize) {
            this.entrySize = entrySize;
        }

        public long getEntrySize() {
            return entrySize;
        }

        public abstract long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException;

        public abstract long write(ByteBuffer buffer) throws BufferOverflowException, ReadOnlyBufferException;

    }

    public class NullEntry extends Entry {

        public NullEntry(int entrySize) {
            super(entrySize);
        }

        @Override
        public long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException {
            byte[] nullBuffer = new byte[entrySize];
            buffer.get(nullBuffer, 0, entrySize);
            return 0;
        }

        @Override
        public long write(ByteBuffer buffer) throws BufferOverflowException, ReadOnlyBufferException {
            return 0;
        }
    }


    /*
     * ENTRY TYPE GROUP: (Header + Payload)
     * +-------+--------+-------------------------------------------+
     * | TYPE  | length |        Group ID                           |
     * +-------+--------+-------------------------------------------+
     *     1       1                      8
     */
    private class GroupEntry  extends Entry {

        public static final int GROUP_GID_SIZE = HashUtil.GROUP_ID_SIZE;

        private String group_id_base64;

        public GroupEntry(int entrySize) {
            super(entrySize);
            this.group_id_base64 = null;
        }

        public GroupEntry(String gid) {
            super(GROUP_GID_SIZE);
            this.group_id_base64 = gid;
        }

        @Override
        public long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException {
            byte[] gid = new byte[GROUP_GID_SIZE];
            buffer.get(gid,0,GROUP_GID_SIZE);
            this.group_id_base64 = Base64.encodeToString(gid,0,GROUP_GID_SIZE,Base64.NO_WRAP);
            return  GROUP_GID_SIZE;
        }

        @Override
        public long write(ByteBuffer buffer) throws BufferOverflowException, ReadOnlyBufferException{
            /* write entry header */
            buffer.put((byte)ENTRY_TYPE_GROUP);
            buffer.put((byte)entrySize);

            /* write entry payload */
            byte[] gid = Base64.decode(group_id_base64, Base64.NO_WRAP);
            buffer.put(gid,0,GROUP_GID_SIZE);
            return (HEADER_SIZE+entrySize);
        }
    }




    /*
     * ENTRY TYPE TAG INTEREST (Header + Payload)
     * +-------+--------+----------+--------------------------------+
     * | TYPE  | length | Interest |           hashtag              |
     * +-------+--------+----------+--------------------------------+
     *     1       1         1                   8
     */
    private class TagInterestEntry extends Entry {
        public static final int  TAG_INTEREST_SIZE = 1;

        private int    levelOfInterest;
        private String hashtag;

        public TagInterestEntry(int entrySize) {
            super(entrySize);
            this.hashtag = null;
            this.levelOfInterest = -1;
        }

        public TagInterestEntry(String hashtag, byte levelOfInterest) {
            super(TAG_INTEREST_SIZE + hashtag.getBytes(Charset.forName("UTF-8")).length);
            this.hashtag = hashtag;
            this.levelOfInterest = levelOfInterest;
        }

        @Override
        public long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException{
            this.levelOfInterest = (buffer.get() & 0xFF);
            int hashtagsize = entrySize-1;
            byte[] hashtagbytes = new byte[hashtagsize];
            buffer.get(hashtagbytes,0,hashtagsize);
            this.hashtag = new String(hashtagbytes);
            return (TAG_INTEREST_SIZE+hashtagsize);
        }

        @Override
        public long write(ByteBuffer buffer) throws BufferOverflowException, ReadOnlyBufferException{
            /* write entry header */
            buffer.put((byte)ENTRY_TYPE_TAG);
            buffer.put((byte)entrySize);

            byte[] hashtagBytes = hashtag.getBytes(Charset.forName("UTF-8"));

            /* write entry payload */
            buffer.put((byte)levelOfInterest);
            buffer.put(hashtagBytes, 0, (byte)hashtagBytes.length);
            return (HEADER_SIZE+TAG_INTEREST_SIZE+(byte)hashtagBytes.length);
        }
    }
}
