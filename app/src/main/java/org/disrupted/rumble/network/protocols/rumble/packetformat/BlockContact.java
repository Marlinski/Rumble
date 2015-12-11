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

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.events.ContactInformationSent;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.EncryptedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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
 * DATA structures according to the TYPE value:
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
 * @author Lucien Loiseau
 */
public class BlockContact extends Block {

    private static final String TAG = "BlockContact";

    /*
     * Byte size
     */
    private static final int FIELD_UID_SIZE            = Contact.CONTACT_UID_RAW_SIZE;
    private static final int FIELD_AUTHOR_LENGTH_SIZE  = 1;

    private static final int MIN_PAYLOAD_SIZE       = (
              FIELD_UID_SIZE
            + FIELD_AUTHOR_LENGTH_SIZE
    );
    private static final int MAX_BLOCK_CONTACT_SIZE =  2048;

    public Contact contact;
    public int     flags;

    public BlockContact(BlockHeader header) {
        super(header);
    }

    public BlockContact(CommandSendLocalInformation command) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_CONTACT);
        this.contact = command.getContact();
        this.flags   = command.getFlags();
    }

    public void sanityCheck() throws MalformedBlockPayload {
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_CONTACT)
            throw new MalformedBlockPayload("Block type BLOCKTYPE_CONTACT expected",0);
        if((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_BLOCK_CONTACT_SIZE))
            throw new MalformedBlockPayload("header.length is too short: "+header.getBlockLength(), 0);
    }

    @Override
    public long readBlock(InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {

        long timeToTransfer = System.nanoTime();

        /* read the block */
        long readleft = header.getBlockLength();
        byte[] blockBuffer = new byte[(int)header.getBlockLength()];
        int count = in.read(blockBuffer, 0, (int)header.getBlockLength());
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count <  (int)header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected", count);

        BlockDebug.d(TAG, "BlockContact received ("+count+" bytes): "+ Arrays.toString(blockBuffer));

        /* process the read buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            // read the UID raw
            byte[] uid = new byte[FIELD_UID_SIZE];
            byteBuffer.get(uid,0,FIELD_UID_SIZE);

            // encode the UID in base64
            String user_id_base64 = Base64.encodeToString(uid, 0, FIELD_UID_SIZE,Base64.NO_WRAP);
            readleft -= FIELD_UID_SIZE;

            // read the namesize field
            int author_name_length = (byteBuffer.get() & 0xFF);
            readleft -= FIELD_AUTHOR_LENGTH_SIZE;
            if((author_name_length < 0) || (author_name_length > readleft) || (author_name_length > Contact.CONTACT_NAME_MAX_SIZE))
                throw new MalformedBlockPayload("contact name length is too long:", author_name_length);

            // read the name
            byte[] author_name = new byte[author_name_length];
            byteBuffer.get(author_name,0,author_name_length);
            readleft -= author_name_length;

            contact = new Contact(new String(author_name),user_id_base64,false);

            // read the optional fields
            while(readleft > 2) {
                int entryType = byteBuffer.get();          // FIELD_TYPE_SIZE
                int entrySize = (byteBuffer.get() & 0xff); // FIELD_LENGTH_SIZE
                if(entrySize > readleft)
                    throw new MalformedBlockPayload("entry larger than what's left to read",readleft);
                Entry entry;
                switch (entryType) {
                    case Entry.ENTRY_TYPE_GROUP:
                        entry = new GroupEntry(entrySize);
                        entry.read(byteBuffer);
                        contact.addGroup(((GroupEntry)entry).group_id_base64);
                        this.flags |= Contact.FLAG_GROUP_LIST;
                        break;
                    case Entry.ENTRY_TYPE_TAG:
                        entry = new TagInterestEntry(entrySize);
                        entry.read(byteBuffer);
                        contact.addTagInterest(((TagInterestEntry)entry).hashtag, ((TagInterestEntry)entry).levelOfInterest);
                        this.flags |= Contact.FLAG_TAG_INTEREST;
                        break;
                    default:
                        entry = new NullEntry(entrySize);
                        entry.read(byteBuffer);
                        break;
                }
                readleft -= (entry.getEntrySize());
            }
            contact.lastMet(System.currentTimeMillis());

            return header.getBlockLength();
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", count);
        }

    }

    @Override
    public long writeBlock(OutputStream out, EncryptedOutputStream eos) throws IOException, InputOutputStreamException {
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
                buffersize += bufferEntry.getEntrySize();
            }
        }
        if((flags & Contact.FLAG_GROUP_LIST) == Contact.FLAG_GROUP_LIST) {
            for (String gid : contact.getJoinedGroupIDs()) {
                GroupEntry bufferEntry = new GroupEntry(gid);
                entries.add(bufferEntry);
                buffersize += bufferEntry.getEntrySize();
            }
        }
        header.setPayloadLength(buffersize);

        /* prepare the block payload buffer */
        byte[] buffer = new byte[buffersize];
        ByteBuffer blockBuffer = ByteBuffer.wrap(buffer);

        blockBuffer.put(author_id, 0, FIELD_UID_SIZE);
        blockBuffer.put((byte)Math.min(author_name.length, Contact.CONTACT_NAME_MAX_SIZE));
        blockBuffer.put(author_name, 0, Math.min(author_name.length, Contact.CONTACT_NAME_MAX_SIZE));

        for( Entry entry : entries ) {
            try {
                entry.write(blockBuffer);
            } catch (BufferOverflowException e) {
                BlockDebug.e(TAG, "BufferOverFlow",e);
            } catch (ReadOnlyBufferException e) {
                BlockDebug.e(TAG, "ReadOnlyBufferException",e);
            }
        }

        /* send the BlockHeader and the BlockPayload */
        header.writeBlockHeader(out);
        out.write(blockBuffer.array(), 0, buffersize);
        BlockDebug.d(TAG, "BlockContact sent (" + buffersize + " bytes): " + Arrays.toString(blockBuffer.array()));

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

        public Entry(int entrySize){
            this.entrySize = entrySize;
        }

        public long getEntrySize() {
            return entrySize+HEADER_SIZE;
        }

        public abstract long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException, MalformedBlockPayload;

        public abstract long write(ByteBuffer buffer) throws BufferOverflowException, ReadOnlyBufferException;

    }

    public class NullEntry extends Entry {

        public NullEntry(int entrySize) throws MalformedBlockPayload{
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

        private String group_id_base64;

        public GroupEntry(int entrySize) throws MalformedBlockPayload{
            super(entrySize);
            if((entrySize < 0) || (entrySize > (Group.GROUP_GID_RAW_SIZE)))
                throw new MalformedBlockPayload("wrong group entry size ",entrySize);
            this.group_id_base64 = null;
        }

        public GroupEntry(String gid){
            super(Group.GROUP_GID_RAW_SIZE);
            this.group_id_base64 = gid;
        }

        @Override
        public long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException, MalformedBlockPayload {
            byte[] gid = new byte[Group.GROUP_GID_RAW_SIZE];
            buffer.get(gid, 0, Group.GROUP_GID_RAW_SIZE);
            this.group_id_base64 = Base64.encodeToString(gid,0,Group.GROUP_GID_RAW_SIZE,Base64.NO_WRAP);
            return  Group.GROUP_GID_RAW_SIZE;
        }

        @Override
        public long write(ByteBuffer buffer) throws BufferOverflowException, ReadOnlyBufferException{
            /* write entry header */
            buffer.put((byte)ENTRY_TYPE_GROUP);
            buffer.put((byte)entrySize);

            /* write entry payload */
            byte[] gid = Base64.decode(group_id_base64, Base64.NO_WRAP);
            buffer.put(gid,0,Group.GROUP_GID_RAW_SIZE);
            return (HEADER_SIZE+entrySize);
        }
    }




    /*
     * ENTRY TYPE TAG INTEREST (Header + Payload)
     * +-------+----------+----------+--------------------------------+
     * | TYPE  |  length  | Interest |           hashtag              |
     * +-------+----------+----------+--------------------------------+
     *     1       1         1                   8
     */
    private class TagInterestEntry extends Entry {
        public static final int  TAG_INTEREST_SIZE = 1;

        private int    levelOfInterest;
        private String hashtag;

        public TagInterestEntry(int entrySize)  throws MalformedBlockPayload {
            super(entrySize);
            if((entrySize < 1) || (entrySize > (PushStatus.STATUS_HASHTAG_MAX_SIZE+1)))
                throw new MalformedBlockPayload("wrong TagInterest entry size",entrySize);
            this.hashtag = null;
            this.levelOfInterest = -1;
        }

        public TagInterestEntry(String hashtag, byte levelOfInterest) {
            super(TAG_INTEREST_SIZE + hashtag.getBytes(Charset.forName("UTF-8")).length);
            this.hashtag = hashtag;
            this.levelOfInterest = levelOfInterest;
        }

        @Override
        public long read(ByteBuffer buffer) throws IndexOutOfBoundsException, BufferUnderflowException, MalformedBlockPayload{
            this.levelOfInterest = (buffer.get() & 0xFF);
            int hashtagSize = entrySize-1;
            byte[] hashtagBytes = new byte[hashtagSize];
            buffer.get(hashtagBytes,0,hashtagSize);
            this.hashtag = new String(hashtagBytes);
            return (TAG_INTEREST_SIZE+hashtagSize);
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
