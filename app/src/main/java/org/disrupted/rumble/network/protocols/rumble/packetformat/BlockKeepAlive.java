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

import org.disrupted.rumble.network.linklayer.UnicastConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.CommandSendKeepAlive;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedBlockPayload;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Marlinski
 */
public class BlockKeepAlive extends Block {

    public static final String TAG = "BlockKeepAlive";
    public static final int BUFFER_SIZE = 1024;

    public BlockKeepAlive(BlockHeader header) {
        super(header);
    }

    public BlockKeepAlive(CommandSendKeepAlive command) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_KEEPALIVE);
        header.setPayloadLength(0);
    }

    @Override
    public long readBlock(ProtocolChannel channel, InputStream in) throws MalformedBlockPayload, IOException, InputOutputStreamException {
        return 0;
    }

    @Override
    public long writeBlock(ProtocolChannel channel, OutputStream out) throws IOException, InputOutputStreamException {
        header.writeBlockHeader(out);
        return BlockHeader.BLOCK_HEADER_LENGTH;
    }

    @Override
    public void dismiss() {
    }
}
