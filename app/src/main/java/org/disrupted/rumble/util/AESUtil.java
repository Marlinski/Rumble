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

package org.disrupted.rumble.util;

/**
 * @author Marlinski
 */
import android.util.Base64;
import android.util.Log;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {

    public static final String TAG = "AESUtil";
    public static final int KEYSIZE = 128;
    public static final int IVSIZE = 16;

    public static SecretKey generateRandomAESKey() throws NoSuchAlgorithmException{
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(KEYSIZE);
        return kgen.generateKey();
    }

    public static long expectedEncryptedSize(long size) {
        return size + (IVSIZE - (size % IVSIZE));
    }

    public static SecretKey getSecretKeyFromByteArray(byte[] keyBlob) {
        if(keyBlob.length == 0)
            return null;
        else
            return new SecretKeySpec(keyBlob, "AES");
    }

    public static byte[] generateRandomIV() throws NoSuchAlgorithmException{
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");
        byte[] iv = new byte[IVSIZE];
        secureRandom.nextBytes(iv);
        return iv;
    }

    public static byte[] encryptBlock(byte[] plainData, SecretKey key, byte[] ivBytes) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ivBytes));
        return cipher.doFinal(plainData);
    }

    public static byte[] decryptBlock(byte[] encryptedData, SecretKey key, byte[] ivBytes) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivBytes));
        return cipher.doFinal(encryptedData);
    }

    public static CipherOutputStream getCipherOutputStream(OutputStream out, SecretKey key, byte[] ivBytes) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        OutputStreamNonClosed osnc = new OutputStreamNonClosed(out);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ivBytes));
        return new CipherOutputStream(osnc, cipher);
    }

    public static CipherInputStream getCipherInputStream(InputStream in, SecretKey key, byte[] ivBytes) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        InputStreamNonClosed isnc = new InputStreamNonClosed(in);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivBytes));
        return new CipherInputStream(isnc, cipher);
    }

    /*
     * CipherInputStream and CipherOutputStream closes the underlying Stream when closed
     * this behaviour It is not convenient when used over a socket, so we create new FilterStream
     * that do nothing when closed.
     */
    public static class OutputStreamNonClosed extends FilterOutputStream {
        public OutputStreamNonClosed(OutputStream out) {
            super(out);
        }
        @Override
        public void close() throws IOException {
        }
    }
    public static class InputStreamNonClosed extends FilterInputStream {
        public InputStreamNonClosed(InputStream in) {
            super(in);
        }
        @Override
        public void close() throws IOException {
        }
    }
}