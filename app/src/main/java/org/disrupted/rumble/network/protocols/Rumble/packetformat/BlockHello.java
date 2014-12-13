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

package org.disrupted.rumble.network.protocols.Rumble.packetformat;

import android.util.Log;

import org.disrupted.rumble.message.Message;
import org.disrupted.rumble.message.MessageHello;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.BufferMismatchBlockSize;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.MalformedBlock;

/**
 * @author Marlinski
 */
public class BlockHello extends Block {

    private static final String TAG = "BlockHello";

    public static final int SUBTYPE = 0x01; //hello

    public BlockHello() {
        super(new BlockHeader(), null);
        header.setType(BlockHeader.Type.PUSH);
        header.setSubtype(SUBTYPE);
        header.setLastBlock(true);
        header.setBlockLength(0);
        payload = null;
    }

    public BlockHello(BlockHeader header) {
        super(header, null);
    }

    @Override
    public void readBuffer() throws BufferMismatchBlockSize {
    }

    @Override
    public int getLength() {
        return header.HEADER_LENGTH;
    }

    @Override
    public MessageHello getMessage() throws MalformedBlock {
        return new MessageHello();
    }
}
