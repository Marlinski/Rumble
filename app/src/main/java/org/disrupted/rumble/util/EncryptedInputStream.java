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

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;

import javax.crypto.Cipher;
import javax.crypto.NullCipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.BadPaddingException;

/**
 * @author Lucien Loiseau
 */
public class EncryptedInputStream extends FilterInputStream {
    private Cipher cipher;
    private InputStream input;

    private byte[] ibuffer = new byte[512];
    private byte[] obuffer;
    private int limit = -1;
    private boolean done = false;

    private int ostart = 0;
    private int ofinish = 0;

    private boolean propagateClose = false;

    public void setLimit(int clearsize) {
        done = false;
        ostart = 0;
        ofinish = 0;
        int blocksize = cipher.getBlockSize();
        if(blocksize > 0) {
            String[] pieces = cipher.getAlgorithm().split("/");
            if(pieces.length != 3)
                return;
            String padding = pieces[2].trim();
            if(padding.equals("NoPadding"))
                limit = clearsize - (clearsize % blocksize);
            else
                limit = clearsize + (blocksize - (clearsize % blocksize));
        } else {
            // not a block cipher
            limit = clearsize;
        }
    }

    public void setHardLimit(int hardLimit) {
        limit = hardLimit;
    }

    private int getMoreData() throws IOException {
        if (done) return -1;
        int max = ibuffer.length;
        if(limit >= 0)
            max = Math.min(limit,ibuffer.length);
        int readin = input.read(ibuffer,0,max);

        // end of stream reached, saving the ouput of doFinal() in obuffer
        if (readin == -1) {
            done = true;
            try {
                obuffer = cipher.doFinal();
            }
            catch (IllegalBlockSizeException e) {obuffer = null;}
            catch (BadPaddingException e) {obuffer = null;}
            if (obuffer == null) {
                return -1;
            } else {
                ostart = 0;
                ofinish = obuffer.length;
                return ofinish;
            }
        }

        // data read, saving output of update() in obuffer
        try {
            obuffer = cipher.update(ibuffer, 0, readin);
        } catch (IllegalStateException e) {obuffer = null;};

        // limit is reached, saving output of doFinal in obuffer too
        if ((limit >= 0) && ((limit -= readin) == 0)) {
            done = true;
            byte[] last;
            try {
                last = cipher.doFinal();
            }
            catch (IllegalBlockSizeException e) {last = null;}
            catch (BadPaddingException e) {last = null;}
            if(last != null) {
                if(obuffer == null) {
                    obuffer = last;
                } else {
                    byte[] assembled = new byte[obuffer.length+last.length];
                    System.arraycopy(obuffer, 0, assembled, 0, obuffer.length);
                    System.arraycopy(last, 0, assembled, obuffer.length, last.length);
                    obuffer = assembled;
                }
            }
        }

        ostart = 0;
        if (obuffer == null)
            ofinish = 0;
        else
            ofinish = obuffer.length;
        return ofinish;
    }

    public EncryptedInputStream(InputStream is, Cipher c) {
        super(is);
        input = is;
        cipher = c;
    }

    protected EncryptedInputStream(InputStream is) {
        super(is);
        input = is;
        cipher = new NullCipher();
    }

    public int read() throws IOException {
        if (ostart >= ofinish) {
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        return ((int) obuffer[ostart++] & 0xff);
    };

    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {
        if (ostart >= ofinish) {
            int i = 0;
            while (i == 0) i = getMoreData();
            if (i == -1) return -1;
        }
        if (len <= 0) {
            return 0;
        }
        int available = ofinish - ostart;
        if (len < available) available = len;
        if (b != null) {
            System.arraycopy(obuffer, ostart, b, off, available);
        }
        ostart = ostart + available;
        return available;
    }

    public long skip(long n) throws IOException {
        int available = ofinish - ostart;
        if (n > available) {
            n = available;
        }
        if (n < 0) {
            return 0;
        }
        ostart += n;
        return n;
    }

    public int available() throws IOException {
        return (ofinish - ostart);
    }

    public void setPropagateClose(boolean propagateClose) {
        this.propagateClose = propagateClose;
    }

    public void close() throws IOException {
        if (propagateClose)
            in.close();
        try {
            // throw away the unprocessed data
            cipher.doFinal();
        }
        catch (BadPaddingException ex) {
        }
        catch (IllegalBlockSizeException ex) {
        }
        ostart = 0;
        ofinish = 0;
    }

    public boolean markSupported() {
        return false;
    }
}