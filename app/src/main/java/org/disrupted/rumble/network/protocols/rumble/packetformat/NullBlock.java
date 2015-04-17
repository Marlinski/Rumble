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

import android.util.Log;

import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;

import java.io.IOException;

/**
 * @author Marlinski
 */
public class NullBlock extends Block {

    public static final String TAG = "NullBlock";
    public static final int BUFFER_SIZE = 1024;

    public NullBlock(BlockHeader header) {
        super(header);
        Log.d(TAG, "[+] null block: "+header.toString());
    }

    @Override
    public long readBlock(LinkLayerConnection con) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        byte[] buffer = new byte[BUFFER_SIZE];
        long readleft = header.getBlockLength();
        while(readleft > 0) {
            long max_read = Math.min((long)BUFFER_SIZE,readleft);
            int read = con.getInputStream().read(buffer, 0, (int)max_read);
            readleft -= read;
        }
        return 0;
    }

    @Override
    public long writeBlock(LinkLayerConnection con) throws IOException, InputOutputStreamException {
        return 0;
    }

    @Override
    public void dismiss() {

    }
}
