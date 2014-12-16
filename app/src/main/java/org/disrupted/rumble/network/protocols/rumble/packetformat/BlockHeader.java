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

import android.util.Log;

import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.BufferMismatchBlockSize;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * The Basic Rumble Block is the common header for every rumble block
 * it is constituted of the following:
 *
 *   1 byte        2 bytes             2 bytes
 * +---------+-------------------+-------------------+
 * | Version |   Block control   |   Block Length    |
 * +---------+-------------------+-------------------+
 *          /                    \
 *     ____/                      \________________
 *    /                                            \
 *    +-----+-----------------+--+--+--+--+--+--+--+--+
 *    |Type |    Subtype      | R| R| R| R| R| R| R| L|
 *    +-----+-----------------+--+--+--+--+--+--+--+--+
 *     0  1  2  3  4  5  6  7  8  9  0  1  2  3  4  5
 *
 * -- Version: it holds the higher version of the protocol the device is capable of
 * -- Block Control: it holds the following information:
 *
 *       Type: RESERVED, REQUEST, RESPONSE or PUSH
 *       Subtype: The type of the block
 *       R: Reserved
 *       L: Last Block Flag
 *
 * -- Block Length: it holds the length of the following payload
 *
 * @author Marlinski
 */
public class BlockHeader implements BlockBuilder {

    private static final String TAG = "BlockHeader";
    private static final int VERSION_ID = 1;

    private static final int VERSION_BITSIZE = 8;
    private static final int TYPE_BITSIZE = 2;
    private static final int SUBTYPE_BITSIZE = 6;
    private static final int FLAG_SIZE = 8;
    private static final int BLOCK_LENGTH = 16;

    public  static final int HEADER_LENGTH = (( VERSION_BITSIZE +
                                                TYPE_BITSIZE +
                                                SUBTYPE_BITSIZE +
                                                FLAG_SIZE +
                                                BLOCK_LENGTH) / 8);

    public enum Type {
        UNDEFINED,  /* not used */
        REQUEST,    /* requesting an information (name, publickey, forwardertable, etc.) */
        RESPONSE,   /* response to a request */
        PUSH;       /* sending information without being asked to */
    }

    private int     version;
    private Type    type;
    private int     subtype;
    private boolean reserved0;
    private boolean reserved1;
    private boolean reserved2;
    private boolean reserved3;
    private boolean reserved4;
    private boolean reserved5;
    private boolean reserved6;
    private boolean lastBlockFlag;
    private int payload_length;

    private byte[] headerBuffer;

    public BlockHeader() {
        headerBuffer = null;
        version = VERSION_ID;
        type = Type.PUSH;
        subtype = 0;
        reserved0 = false;
        reserved1 = false;
        reserved2 = false;
        reserved3 = false;
        reserved4 = false;
        reserved5 = false;
        reserved6 = false;
        lastBlockFlag = true;
        payload_length = 0;
    }

    public BlockHeader(byte[] blockBuffer) {
        this.headerBuffer = blockBuffer;
    }

    @Override
    public void readBuffer() throws BufferMismatchBlockSize, MalformedRumblePacket {
        if(headerBuffer.length < HEADER_LENGTH)
            throw new BufferMismatchBlockSize();

        ByteBuffer byteBuffer = ByteBuffer.wrap(headerBuffer);

        version            = ((int) byteBuffer.get() & 0xff);
        int block_control  = ((int) byteBuffer.get() & 0xff);
        int flags          = ((int) byteBuffer.get() & 0xff);
        payload_length     = ((int) byteBuffer.getShort() & 0xffff);

        switch (block_control >>> 6){
            case 0: type = Type.UNDEFINED; break;
            case 1: type = Type.REQUEST; break;
            case 2: type = Type.RESPONSE; break;
            case 3: type = Type.PUSH; break;
            default: throw  new MalformedRumblePacket("Type "+(block_control >>> 6)+" is unknown");
        }
        subtype = (block_control & 0x3F);
        reserved0 = ((flags & 0x80) == 0x80);
        reserved1 = ((flags & 0x40) == 0x40);
        reserved2 = ((flags & 0x20) == 0x20);
        reserved3 = ((flags & 0x10) == 0x10);
        reserved4 = ((flags & 0x08) == 0x08);
        reserved5 = ((flags & 0x04) == 0x04);
        reserved6 = ((flags & 0x02) == 0x02);
        lastBlockFlag = ((flags & 0x01) == 0x01);
    }

    @Override
    public byte[] getBytes() throws MalformedRumblePacket{
        try {
            ByteBuffer bufferBlockHeader = ByteBuffer.allocate(HEADER_LENGTH);
            bufferBlockHeader.put((byte) (version & 0xff));
            switch (type) {
                case UNDEFINED: bufferBlockHeader.put((byte) ((0x00 << 6) | (0xff & subtype))); break;
                case REQUEST:   bufferBlockHeader.put((byte) ((0x01 << 6) | (0xff & subtype))); break;
                case RESPONSE:  bufferBlockHeader.put((byte) ((0x02 << 6) | (0xff & subtype))); break;
                case PUSH:      bufferBlockHeader.put((byte) ((0x03 << 6) | (0xff & subtype))); break;
            }
            bufferBlockHeader.put((byte) ((reserved0 ? 1 : 0) << 7 |
                    (reserved1 ? 1 : 0) << 6 |
                    (reserved2 ? 1 : 0) << 5 |
                    (reserved3 ? 1 : 0) << 4 |
                    (reserved4 ? 1 : 0) << 3 |
                    (reserved5 ? 1 : 0) << 2 |
                    (reserved6 ? 1 : 0) << 1 |
                    (lastBlockFlag ? 1 : 0)));
            bufferBlockHeader.putShort((short) payload_length);
            return bufferBlockHeader.array();
        }
        catch(BufferOverflowException e) {
            Log.e(TAG, "[!] header overflow the buffer");
        }
        return null;
    }


    public int getVersion()      {   return version;   }
    public Type getType()        {   return type;      }
    public int getSubtype()      {   return subtype;   }
    public boolean isReserved0() {   return reserved0; }
    public boolean isReserved1() {   return reserved1; }
    public boolean isReserved2() {   return reserved2; }
    public boolean isReserved3() {   return reserved3; }
    public boolean isReserved4() {   return reserved4; }
    public boolean isReserved5() {   return reserved5; }
    public boolean isReserved6() {   return reserved6; }
    public boolean isLastBlock() {   return lastBlockFlag; }
    public int getPayloadLength(){   return payload_length; }


    public void setVersion(int version)         {  this.version = version;  }
    public void setType(Type type)              {  this.type = type;   }
    public void setSubtype(int subtype)         {  this.subtype = subtype; }
    public void setPayloadLength(int length)    {  this.payload_length = length; }
    public void setReserved0(boolean reserved0) {  this.reserved0 = reserved0; }
    public void setReserved1(boolean reserved1) {  this.reserved1 = reserved1; }
    public void setReserved2(boolean reserved2) {  this.reserved2 = reserved2; }
    public void setReserved3(boolean reserved3) {  this.reserved3 = reserved3; }
    public void setReserved4(boolean reserved4) {  this.reserved4 = reserved4; }
    public void setReserved5(boolean reserved5) {  this.reserved5 = reserved5; }
    public void setReserved6(boolean reserved6) {  this.reserved6 = reserved6; }
    public void setLastBlock(boolean lastBlockFlag) {  this.lastBlockFlag = lastBlockFlag; }

}
