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

import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockHeader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The BlockHeader is simply constituted of a BlockType and its subsequent Length
 *
 *
 *   1 byte        2 bytes         1 byte             8 bytes (long)
 * +---------+-------------------+----------+---------------------------------+
 * | Version |   Block control   |   Type   |          Block Length           |
 * +---------+-------------------+----------+---------------------------------+
 *          /                    \
 *     ____/                      \___________________
 *    /                                               \
 *    +-----------------------+--+--+--+--+--+--+--+--+
 *    |   Transaction Type    | R| R| R| R| R| R| R| R|
 *    +-----------------------+--+--+--+--+--+--+--+--+
 *     0  1  2  3  4  5  6  7  8  9  1  1  1  1  1  1
 *                                   0  1  2  3  4  5
 *
 * -- Type: the type of the current block
 * -- R: Reserved
 * -- Block Type: type of the payload
 * -- Block Length: the length of the Following Block
 *
 * @author Lucien Loiseau
 */
public class BlockHeader {

    private static final String TAG = "BlockHeader";
    private static final int VERSION_ID = 1;

    /* header field size */
    private static final int VERSION_BITSIZE     = 8;        // sizeof byte
    private static final int TRANSACTION_TYPE_BITSIZE = 8;   // sizeof byte
    private static final int FLAG_BITSIZE        = 8;        // sizeof byte
    private static final int BLOCKTYPE_BITSIZE   = 8;        // sizeof byte
    private static final int BLOCKLENGTH_BITSIZE = 64;       // sizeof long

    public  static final int BLOCK_HEADER_LENGTH = (
            (       VERSION_BITSIZE +
                    TRANSACTION_TYPE_BITSIZE +
                    FLAG_BITSIZE +
                    BLOCKTYPE_BITSIZE +
                    BLOCKLENGTH_BITSIZE) / 8);

    private int     version;
    private int     transaction_type;
    private boolean reserved0;
    private boolean reserved1;
    private boolean reserved2;
    private boolean reserved3;
    private boolean reserved4;
    private boolean reserved5;
    private boolean reserved6;
    private boolean last_block;
    private int     block_type;
    private long    payload_length;

    public static final int TRANSACTION_TYPE_UNDEFINED = 0x00;
    public static final int TRANSACTION_TYPE_REQUEST   = 0x01;
    public static final int TRANSACTION_TYPE_RESPONSE  = 0x02;
    public static final int TRANSACTION_TYPE_PUSH      = 0x03;

    public static final int BLOCKTYPE_KEEPALIVE     = 0x00;
    public static final int BLOCKTYPE_PUSH_STATUS   = 0x01;
    public static final int BLOCKTYPE_FILE          = 0x02;
    public static final int BLOCKTYPE_CONTACT       = 0x03;
    public static final int BLOCKTYPE_CHAT_MESSAGE  = 0x04;
    public static final int BLOCK_CIPHER            = 0x05;
    public static final int BLOCK_NULL              = 0xff;

    public BlockHeader() {
        version = VERSION_ID;
        transaction_type = TRANSACTION_TYPE_PUSH;
        reserved0 = false;
        reserved1 = false;
        reserved2 = false;
        reserved3 = false;
        reserved4 = false;
        reserved5 = false;
        reserved6 = false;
        last_block = true;
        this.block_type = BLOCK_NULL;
        this.payload_length = 0;
    }

    public static BlockHeader readBlockHeader(InputStream in) throws MalformedBlockHeader, IOException {
        BlockHeader ret = new BlockHeader();
        byte[] headerBuffer = new byte[BLOCK_HEADER_LENGTH];

        int count = in.read(headerBuffer, 0, BLOCK_HEADER_LENGTH);
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < BLOCK_HEADER_LENGTH)
            throw new MalformedBlockHeader("read less bytes than expected", count);
        BlockDebug.d(TAG, "BlockHeader received (" + count + " bytes): " + Arrays.toString(headerBuffer));

        ByteBuffer byteBuffer = ByteBuffer.wrap(headerBuffer);

        ret.version          = ((int) byteBuffer.get() & 0xff);
        ret.transaction_type = ((int) byteBuffer.get() & 0xff);

        int flags      = ((int) byteBuffer.get() & 0xff);
        ret.reserved0  = ((flags & 0x80) == 0x80);
        ret.reserved1  = ((flags & 0x40) == 0x40);
        ret.reserved2  = ((flags & 0x20) == 0x20);
        ret.reserved3  = ((flags & 0x10) == 0x10);
        ret.reserved4  = ((flags & 0x08) == 0x08);
        ret.reserved5  = ((flags & 0x04) == 0x04);
        ret.reserved6  = ((flags & 0x02) == 0x02);
        ret.last_block = ((flags & 0x01) == 0x01);
        ret.block_type     = ((int) byteBuffer.get() & 0xff);
        ret.payload_length = byteBuffer.getLong();

        return ret;
    }

    public long writeBlockHeader(OutputStream out) throws IOException {
        ByteBuffer bufferBlockHeader = ByteBuffer.allocate(BLOCK_HEADER_LENGTH);
        try {
            bufferBlockHeader.put((byte)(version & 0xff));
            bufferBlockHeader.put((byte)(transaction_type & 0xff));
            bufferBlockHeader.put((byte) (
                    (reserved0 ? 1 : 0) << 7 |
                    (reserved1 ? 1 : 0) << 6 |
                    (reserved2 ? 1 : 0) << 5 |
                    (reserved3 ? 1 : 0) << 4 |
                    (reserved4 ? 1 : 0) << 3 |
                    (reserved5 ? 1 : 0) << 2 |
                    (reserved6 ? 1 : 0) << 1 |
                    (last_block ? 1 : 0)));
            bufferBlockHeader.put((byte)(block_type & 0xff));
            bufferBlockHeader.putLong(payload_length);

            out.write(bufferBlockHeader.array());
            bufferBlockHeader.clear();
            BlockDebug.d(TAG, "BlockHeader sent (" + bufferBlockHeader.array().length + " bytes): " +Arrays.toString(bufferBlockHeader.array()));
            return BLOCK_HEADER_LENGTH;
        }
        catch(BufferOverflowException e) {
            BlockDebug.e(TAG, "[!] header overflow the buffer");
            bufferBlockHeader.clear();
            return 0;
        }
    }

    public int getVersion()      {   return version;   }
    public int getTransaction() {   return transaction_type; }
    public int getBlockType()   {   return block_type;   }
    public long getBlockLength() {   return payload_length; }
    public boolean isReserved0() {   return reserved0; }
    public boolean isReserved1() {   return reserved1; }
    public boolean isReserved2() {   return reserved2; }
    public boolean isReserved3() {   return reserved3; }
    public boolean isReserved4() {   return reserved4; }
    public boolean isReserved5() {   return reserved5; }
    public boolean isEncrypted() {   return reserved6; }
    public boolean isLastBlock() {   return last_block; }

    public void setVersion(int version)         {  this.version = version;  }
    public void setTransaction(int transaction_type)   {  this.transaction_type = transaction_type;   }
    public void setBlockType(int type)          {  this.block_type = type;   }
    public void setReserved0(boolean reserved0) {  this.reserved0 = reserved0; }
    public void setReserved1(boolean reserved1) {  this.reserved1 = reserved1; }
    public void setReserved2(boolean reserved2) {  this.reserved2 = reserved2; }
    public void setReserved3(boolean reserved3) {  this.reserved3 = reserved3; }
    public void setReserved4(boolean reserved4) {  this.reserved4 = reserved4; }
    public void setReserved5(boolean reserved5) {  this.reserved5 = reserved5; }
    public void setEncrypted(boolean reserved6) {  this.reserved6 = reserved6; }
    public void setLastBlock(boolean last_block) { this.last_block = last_block; }
    public void setPayloadLength(long length) { this.payload_length = length; }

    @Override
    public String toString() {
        return "type:"+block_type+ " payload:"+ payload_length;
    }
}

