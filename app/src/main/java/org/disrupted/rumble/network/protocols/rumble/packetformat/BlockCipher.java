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

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.CryptoUtil;
import org.disrupted.rumble.util.EncryptedInputStream;
import org.disrupted.rumble.util.EncryptedOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.crypto.SecretKey;

/**
 * A BlockCrypto holds all the information necessary to configure a Key
 *
 * +--------------+--------------+--------------+--------------+
 * |     Type     |  Algorithm   |    Block     |   Padding    |  1 + 1 + 1 + 1
 * +--------------+--------------+--------------+--------------+
 * |                         DATA                              |
 * +------------ // ------------ // ----------- // ------------+
 *
 *                ========================
 *
 * DATA structures according to the TYPE value:
 *
 * BLOC_CIPHER_CLEAR:
 *  ...empty...
 *
 * BLOCK_CIPHER_GROUP:
 * +-----------------------------------------------+
 * |                     GID                       | 8 bytes Group GID
 * +-----------------------------------------------+
 * |               Initialisation                  | 16 bytes or 8 bytes IV
 * |                  Vector                       | (depend on cipher used)
 * +-----------------------------------------------+
 *
 * @author Lucien Loiseau
 */
public class BlockCipher extends Block {

    public static final String TAG = "BlockCipher";

    /* crypto header field size */
    private static final int FIELD_TYPE_SIZE  = 0x01;
    private static final int FIELD_ALGORITHM_SIZE = 0x01;
    private static final int FIELD_BLOCK_SIZE = 0x01;
    private static final int FIELD_PADDING_SIZE = 0x01;

    /* header values */
    public enum CipherType{
        TYPE_CIPHER_CLEAR  (0x00),
        TYPE_CIPHER_GROUP  (0x01),
        TYPE_CIPHER_UNKNOW (0xff);

        public final int value;
        CipherType(int value) {this.value = value;}
        public static CipherType cipherType(int value) {
            switch(value) {
                case 0x00:
                    return TYPE_CIPHER_CLEAR;
                case 0x01:
                    return TYPE_CIPHER_GROUP;
                default:
                    return TYPE_CIPHER_UNKNOW;
            }
        }
    }

    /* data field size BLOCK_CIPHER_GROUP */
    private static final int FIELD_GROUP_GID_SIZE       = Group.GROUP_GID_RAW_SIZE;
    private static final int FIELD_MAX_IV_SIZE          = CryptoUtil.IVSIZE;

    /* block boundaries */
    private static final int MIN_PAYLOAD_SIZE = (
            FIELD_TYPE_SIZE +
            FIELD_ALGORITHM_SIZE +
            FIELD_BLOCK_SIZE +
            FIELD_PADDING_SIZE);

    private static final int MAX_CRYPTO_BLOCK_SIZE = (
            MIN_PAYLOAD_SIZE +
            FIELD_GROUP_GID_SIZE +
            FIELD_MAX_IV_SIZE);


    public CipherType type;
    public CryptoUtil.CipherAlgo    algo;
    public CryptoUtil.CipherBlock   block;
    public CryptoUtil.CipherPadding padding;

    private String gid;
    public String group_id_base64;
    public byte[] ivBytes;

    public BlockCipher(BlockHeader header) {
        super(header);
    }

    public BlockCipher() {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCK_CIPHER);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);

        // default cleartext
        this.type    = CipherType.TYPE_CIPHER_CLEAR;
        this.algo = CryptoUtil.CipherAlgo.ALGO_CLEAR;
        this.block = CryptoUtil.CipherBlock.NO_BLOCK;
        this.padding = CryptoUtil.CipherPadding.NO_PADDING;

        this.gid = null;
        this.ivBytes = null;

    }

    public BlockCipher(String gid, byte[] iv) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCK_CIPHER);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);

        // default AES/CBC/PKCS5Padding
        this.type    = CipherType.TYPE_CIPHER_GROUP;
        this.algo    = CryptoUtil.CipherAlgo.ALGO_AES;
        this.block   = CryptoUtil.CipherBlock.BLOCK_CBC;
        this.padding = CryptoUtil.CipherPadding.PADDING_PKCS5;

        this.gid = gid;
        this.ivBytes = iv;
    }

    public void sanityCheck() throws MalformedBlockPayload {
        if (header.getBlockType() != BlockHeader.BLOCK_CIPHER)
            throw new MalformedBlockPayload("Block type BLOCK_CIPHER expected", 0);
        if ((header.getBlockLength() < MIN_PAYLOAD_SIZE) || (header.getBlockLength() > MAX_CRYPTO_BLOCK_SIZE))
            throw new MalformedBlockPayload("wrong header length parameter: " + header.getBlockLength(), 0);
    }

    @Override
    public long readBlock(InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        sanityCheck();

        /* read the entire block into a block buffer */
        long readleft = header.getBlockLength();
        byte[] blockBuffer = new byte[(int) header.getBlockLength()];
        int count = in.read(blockBuffer, 0, (int) header.getBlockLength());
        if (count < 0)
            throw new IOException("end of stream reached");
        if (count < (int) header.getBlockLength())
            throw new MalformedBlockPayload("read less bytes than expected: "+count+"/"+readleft,count);

        BlockDebug.d(TAG,"BlockCrypto received ("+readleft+" bytes): "+Arrays.toString(blockBuffer));
        /* process the block buffer */
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(blockBuffer);

            type = CipherType.cipherType(byteBuffer.get());
            readleft -= FIELD_TYPE_SIZE;
            algo =  CryptoUtil.CipherAlgo.cipherAlgo(byteBuffer.get());
            readleft -= FIELD_ALGORITHM_SIZE;
            block = CryptoUtil.CipherBlock.cipherBlock(byteBuffer.get());
            readleft -= FIELD_BLOCK_SIZE;
            padding = CryptoUtil.CipherPadding.cipherPadding(byteBuffer.get());
            readleft -= FIELD_PADDING_SIZE;

            switch (type) {
                case TYPE_CIPHER_CLEAR:
                    this.group_id_base64 = null;
                    this.ivBytes = null;
                    break;
                case TYPE_CIPHER_GROUP:
                    byte[] group_id = new byte[FIELD_GROUP_GID_SIZE];
                    byteBuffer.get(group_id, 0, FIELD_GROUP_GID_SIZE);
                    readleft -= FIELD_GROUP_GID_SIZE;

                    byte[] iv = null;
                    if(block.equals(CryptoUtil.CipherBlock.BLOCK_CBC)) {
                        int ivsize = (algo.equals(CryptoUtil.CipherAlgo.ALGO_AES)) ? 16 : 8;
                        iv = new byte[ivsize];
                        byteBuffer.get(iv, 0, ivsize);
                        readleft -= ivsize;
                    }

                    /* configure the keys */
                    group_id_base64 = Base64.encodeToString(group_id, 0, FIELD_GROUP_GID_SIZE, Base64.NO_WRAP);
                    this.ivBytes = iv;
                    break;
                default:
                    // read the rest of the block, if any;
                    byte[] buffer = new byte[100];
                    while (readleft > 0) {
                        long max_read = Math.min((long) 100, readleft);
                        int bytesread = in.read(buffer, 0, (int) max_read);
                        if (bytesread < 0)
                            throw new IOException("End of stream reached");
                        readleft -= bytesread;
                    }
            }

            return header.getBlockLength();
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", header.getBlockLength() - readleft);
        }
    }

    @Override
    public long writeBlock(OutputStream out, EncryptedOutputStream eos) throws IOException, InputOutputStreamException {

        int ivsize = 0;
        if(block.equals(CryptoUtil.CipherBlock.BLOCK_CBC))
            ivsize = (algo.equals(CryptoUtil.CipherAlgo.ALGO_AES)) ? 16 : 8;

        int length = MIN_PAYLOAD_SIZE;
        if(type.equals(CipherType.TYPE_CIPHER_GROUP))
            length += FIELD_GROUP_GID_SIZE+ivsize;

        ByteBuffer blockBuffer= ByteBuffer.allocate(length);
        blockBuffer.put((byte)type.value);
        blockBuffer.put((byte)algo.value);
        blockBuffer.put((byte)block.value);
        blockBuffer.put((byte)padding.value);

        if(type.equals(CipherType.TYPE_CIPHER_GROUP)) {
            byte[] group_id = Base64.decode(gid, Base64.NO_WRAP);
            blockBuffer.put(group_id, 0, FIELD_GROUP_GID_SIZE);
            blockBuffer.put(ivBytes, 0, ivsize);
        }

        /* send the header and the payload */
        header.setPayloadLength(length);
        header.writeBlockHeader(out);
        out.write(blockBuffer.array(), 0, length);
        BlockDebug.d(TAG, "BlockCrypto sent (" + length + " bytes): " + Arrays.toString(blockBuffer.array()));

        return header.getBlockLength()+BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {
        group_id_base64 = null;
        ivBytes = null;
    }
}
