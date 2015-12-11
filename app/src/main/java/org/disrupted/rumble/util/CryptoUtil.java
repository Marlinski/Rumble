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

package org.disrupted.rumble.util;

/**
 * @author Lucien Loiseau
 */

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.network.protocols.rumble.packetformat.BlockDebug;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtil {

    public static final String TAG = "AESUtil";
    public static final int KEYSIZE = 128;
    public static final int IVSIZE = 16;

    public enum CipherAlgo{
        ALGO_CLEAR  (0x00),
        ALGO_AES    (0x01),
        ALGO_DES    (0x02),
        ALGO_DESEDE (0x03),
        ALGO_UNKNOW (0xff);

        public final int value;
        CipherAlgo(int value) {this.value = value;}
        public static CipherAlgo cipherAlgo(int value) {
            switch(value) {
                case 0x00:
                    return ALGO_CLEAR;
                case 0x01:
                    return ALGO_AES;
                case 0x02:
                    return ALGO_DES;
                case 0x03:
                    return ALGO_DESEDE;
                default:
                    return ALGO_UNKNOW;
            }
        }
        @Override
        public String toString() {
            switch(value) {
                case 0x00:
                    return "ClearText";
                case 0x01:
                    return "AES";
                case 0x02:
                    return "DES";
                case 0x03:
                    return "DESede";
                default:
                    return "Unknown";
            }
        }
    }
    public enum CipherBlock{
        NO_BLOCK  (0x00),
        BLOCK_ECB (0x01),
        BLOCK_CBC (0x02),
        BLOCK_UNKNOW (0xff);

        public final int value;
        CipherBlock(int value) {this.value = value;}
        public static CipherBlock cipherBlock(int value) {
            switch(value) {
                case 0x00:
                    return NO_BLOCK;
                case 0x01:
                    return BLOCK_ECB;
                case 0x02:
                    return BLOCK_CBC;
                default:
                    return BLOCK_UNKNOW;
            }
        }
        @Override
        public String toString() {
            switch(value) {
                case 0x00:
                    return "NOBLOCK";
                case 0x01:
                    return "ECB";
                case 0x02:
                    return "CBC";
                default:
                    return "Unknown";
            }
        }
    }
    public enum CipherPadding{
        NO_PADDING    (0x00),
        PADDING_PKCS1 (0x01),
        PADDING_PKCS5 (0x02),
        PADDING_PKCS7 (0x03),
        PADDING_UNKNOWN (0xff);

        public final int value;
        CipherPadding(int value) {this.value = value;}
        public static CipherPadding cipherPadding(int value) {
            switch(value) {
                case 0x00:
                    return NO_PADDING;
                case 0x01:
                    return PADDING_PKCS1;
                case 0x02:
                    return PADDING_PKCS5;
                case 0x03:
                    return PADDING_PKCS7;
                default:
                    return PADDING_UNKNOWN;
            }
        }

        @Override
        public String toString() {
            switch(value) {
                case 0x00:
                    return "NoPadding";
                case 0x01:
                    return "PKCS1Padding";
                case 0x02:
                    return "PKCS5Padding";
                case 0x03:
                    return "PKCS7Padding";
                default:
                    return "Unknown";
            }
        }
    }

    public static SecretKey generateRandomAESKey() throws CryptographicException{
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(KEYSIZE);
            return kgen.generateKey();
        } catch(NoSuchAlgorithmException e) {
            throw new CryptographicException();
        }
    }

    public static SecretKey getSecretKeyFromByteArray(byte[] keyBlob) {
        if(keyBlob.length == 0)
            return null;
        else
            return new SecretKeySpec(keyBlob, "AES");
    }

    public static byte[] generateRandomIV(int size) throws CryptographicException {
        try {
            SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
            byte[] iv = new byte[size];
            secureRandom.nextBytes(iv);
            return iv;
        } catch(NoSuchAlgorithmException e){
            throw new CryptographicException();
        }
    }

    public static EncryptedOutputStream getCipherOutputStream(OutputStream out,
                                                           CipherAlgo algo,
                                                           CipherBlock block,
                                                           CipherPadding pad,
                                                           SecretKey key,
                                                           byte[] ivBytes)  throws CryptographicException {
        try {
            //Log.d(TAG, "setting up EncryptedOutputStream: " + algo + "/" + block + "/" + pad);
            Cipher cipher = Cipher.getInstance(algo+"/"+block+"/"+pad);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ivBytes));
            return new EncryptedOutputStream(out, cipher);
        } catch (InvalidAlgorithmParameterException e) {
            throw new CryptographicException();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptographicException();
        } catch (NoSuchPaddingException e) {
            throw new CryptographicException();
        } catch (InvalidKeyException e) {
            throw new CryptographicException();
        }
    }

    public static EncryptedInputStream getCipherInputStream(InputStream in,
                                                         CipherAlgo algo,
                                                         CipherBlock block,
                                                         CipherPadding pad,
                                                         SecretKey key,
                                                         byte[] ivBytes) throws CryptographicException{
        try {
            //Log.d(TAG, "setting up EncryptedInputStream: " + algo + "/" + block + "/" + pad);
            Cipher cipher = Cipher.getInstance(algo+"/"+block+"/"+pad);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivBytes));
            return new EncryptedInputStream(in, cipher);
        } catch (InvalidAlgorithmParameterException e) {
            throw new CryptographicException();
        } catch (NoSuchAlgorithmException e) {
            throw new CryptographicException();
        } catch (NoSuchPaddingException e) {
            throw new CryptographicException();
        } catch (InvalidKeyException e) {
            throw new CryptographicException();
        }
    }

    public static class CryptographicException extends  Exception {
    }
}