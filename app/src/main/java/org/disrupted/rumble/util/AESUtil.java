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

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESUtil {

    public static final int KEYSIZE = 256;
    public static final int IVSIZE = 16;

    public static SecretKey generateRandomAESKey() throws NoSuchAlgorithmException{
        KeyGenerator kgen = KeyGenerator.getInstance("AES");
        kgen.init(KEYSIZE);
        return kgen.generateKey();
    }

    public static long expectedEncryptedSize(long size) {
        return size + (16 - (size % 16));
    }

    public static SecretKey getSecretKeyFromByteArray(byte[] keyBlob) {
        if(keyBlob.length == 0)
            return null;
        else
            return new SecretKeySpec(keyBlob, "AES");
    }

    public static byte[] generateRandomIV() throws NoSuchAlgorithmException{
        SecureRandom randomSecureRandom = SecureRandom.getInstance("SHA1PRNG");
        byte[] iv = new byte[IVSIZE];
        randomSecureRandom.nextBytes(iv);
        return iv;
    }

    public static String encryptText(String plainText, SecretKey key, byte[] ivBytes) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(ivBytes));
        byte[] encryptedTextBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.encodeToString(encryptedTextBytes, Base64.NO_WRAP);
    }

    public static String decryptText(String encryptedText, SecretKey key, byte[] ivBytes) throws BadPaddingException, IllegalBlockSizeException, UnsupportedEncodingException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(ivBytes));
        byte[] base64decoded = Base64.decode(encryptedText, Base64.NO_WRAP);
        byte[] decryptedTextBytes = cipher.doFinal(base64decoded);
        return new String(decryptedTextBytes);
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

}