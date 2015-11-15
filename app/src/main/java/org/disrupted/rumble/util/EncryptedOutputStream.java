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

import java.io.BufferedOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

/**
 * @author Marlinski
 */
public class EncryptedOutputStream extends CipherOutputStream {
    public EncryptedOutputStream(OutputStream out, Cipher cipher) {
        super(new OutputStreamNonClosed(new BufferedOutputStream(out)), cipher);
    }
    public static class OutputStreamNonClosed extends FilterOutputStream {
        public OutputStreamNonClosed(OutputStream out) {
            super(out);
        }
        @Override
        public void close() throws IOException {
            super.flush();
        }
    }
}
