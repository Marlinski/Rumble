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

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;
import org.disrupted.rumble.util.AESUtil;

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
 * +---------------+--------------+----// // //---+
 * |  CryptoType   |  CipherSuite |   VARIABLE    |  1 byte + 1 byte + VARIABLE
 * +---------------+--------------+----// // //---+
 *
 *
 * CryptoType defines what it is for
 * =================================
 *
 *  1: BLOCK_ENCRYPTION_CLEARTEXT : no encryption
 *  2: BLOCK_ENCRYPTION_GROUP_PARAMETER: the following shall gives all the information to configure a
 *                                    decryption key for a Group
 *
 * CipherSuite defines the type of key
 * ===================================
 *
 * 1: NO CIPHER
 * 2: AES 128 / CBC / PKCS5
 * 3: AES 256 / CBC / PKCS5
 *
 * //////////////////////////////////////////////////////////
 *
 * CryptoType.BLOCK_ENCRYPTION_GROUP_PARAMETER with Cipher AES
 *
 * +-----------------------------------------------+
 * |                     GID                       | 8 bytes Group GID
 * +-----------------------------------------------+
 * |               Initialisation                  | 16 bytes IV
 * |                  Vector                       |
 * +-----------------------------------------------+
 *
 * @author Marlinski
 */
public class BlockCipher extends Block {

    public static final String TAG = "BlockCipher";

    /* crypto header field size */
    private static final int FIELD_CRYPTO_TYPE_SIZE  = 0x01;
    private static final int FIELD_CIPHER_SUITE_SIZE = 0x01;

    /* header values */
    public static final int  CRYPTO_TYPE_CLEARTEXT                        = 0x01;
    public static final int  CRYPTO_TYPE_BLOCK_ENCRYPTION_GROUP_PARAMETER = 0x02;

    public static final int  CIPHER_SUITE_NO_CIPHER        = 0x01;
    public static final int  CIPHER_SUITE_AES128_CBC_PKCS5 = 0x02;
    public static final int  CIPHER_SUITE_AES256_CBC_PKCS5 = 0x03;


    /* key parameter for CRYPTO_TYPE_BLOCK_ENCRYPTION_GROUP_PARAMETER ------------- */
    private static final int FIELD_GROUP_GID_SIZE       = Group.GROUP_GID_RAW_SIZE;
    private static final int FIELD_AES_IV_SIZE          = AESUtil.IVSIZE;


    /* block boundaries */
    private static final int MIN_PAYLOAD_SIZE = (
            FIELD_CRYPTO_TYPE_SIZE +
            FIELD_CIPHER_SUITE_SIZE);

    private static final int MAX_CRYPTO_BLOCK_SIZE = (
            MIN_PAYLOAD_SIZE +
            FIELD_GROUP_GID_SIZE +
            FIELD_AES_IV_SIZE);

    private String gid;
    public SecretKey secretKey;
    public byte[] ivBytes;

    public BlockCipher(BlockHeader header) {
        super(header);
    }

    public BlockCipher() {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCK_CIPHER);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);
        this.gid = null;
        this.secretKey = null;
        this.ivBytes = null;
    }

    public BlockCipher(String gid, byte[] iv) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCK_CIPHER);
        this.header.setTransaction(BlockHeader.TRANSACTION_TYPE_PUSH);
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
    public long readBlock(ProtocolChannel channel, InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {
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

            short cryptoType = byteBuffer.get();
            readleft -= FIELD_CRYPTO_TYPE_SIZE;
            short cipherSuite = byteBuffer.get();
            readleft -= FIELD_CIPHER_SUITE_SIZE;

            switch (cryptoType) {
                case CRYPTO_TYPE_CLEARTEXT:
                    this.secretKey = null;
                    this.ivBytes = null;
                    break;
                case CRYPTO_TYPE_BLOCK_ENCRYPTION_GROUP_PARAMETER:
                    if (cipherSuite != CIPHER_SUITE_AES128_CBC_PKCS5)
                        throw new IOException("Cipher unknown");

                    byte[] group_id = new byte[FIELD_GROUP_GID_SIZE];
                    byteBuffer.get(group_id, 0, FIELD_GROUP_GID_SIZE);
                    readleft -= FIELD_GROUP_GID_SIZE;

                    String group_id_base64 = Base64.encodeToString(group_id, 0, FIELD_GROUP_GID_SIZE, Base64.NO_WRAP);
                    Group group = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext())
                            .getGroup(group_id_base64);
                    if (group == null) // we do not belong to the group
                        throw new IOException("Encryption key is unknown");

                    byte[] iv = new byte[FIELD_AES_IV_SIZE];
                    byteBuffer.get(iv, 0, FIELD_AES_IV_SIZE);
                    readleft -= FIELD_AES_IV_SIZE;

                    /* configure the keys */
                    this.secretKey = group.getGroupKey();
                    this.ivBytes = iv;
                    break;
                default:
                    throw new IOException("Crypto unknown");
            }

            return header.getBlockLength();
        } catch (BufferUnderflowException exception) {
            throw new MalformedBlockPayload("buffer too small", header.getBlockLength() - readleft);
        }
    }

    @Override
    public long writeBlock(ProtocolChannel channel, OutputStream out) throws IOException, InputOutputStreamException {
        /* prepare the buffer */
        int length;
        ByteBuffer blockBuffer;
        if(gid == null) {
            // clear text
            length = MIN_PAYLOAD_SIZE;
            blockBuffer= ByteBuffer.allocate(length);
            blockBuffer.put((byte) CRYPTO_TYPE_CLEARTEXT);
            blockBuffer.put((byte) CIPHER_SUITE_NO_CIPHER);
            header.setLastBlock(true);
        } else {
            byte[] group_id = Base64.decode(gid, Base64.NO_WRAP);
            length = MAX_CRYPTO_BLOCK_SIZE;
            blockBuffer = ByteBuffer.allocate(length);
            blockBuffer.put((byte) CRYPTO_TYPE_BLOCK_ENCRYPTION_GROUP_PARAMETER);
            blockBuffer.put((byte) CIPHER_SUITE_AES128_CBC_PKCS5);
            blockBuffer.put(group_id, 0, FIELD_GROUP_GID_SIZE);
            blockBuffer.put(ivBytes, 0, FIELD_AES_IV_SIZE);
            header.setLastBlock(false);
        }

        /* send the header and the payload */
        header.setPayloadLength(length);
        header.writeBlockHeader(out);
        out.write(blockBuffer.array(), 0, length);
        BlockDebug.d(TAG, "BlockCrypto sent (" + length + " bytes): " + Arrays.toString(blockBuffer.array()));

        return length;
    }

    @Override
    public void dismiss() {
        secretKey = null;
        ivBytes = null;
    }
}
