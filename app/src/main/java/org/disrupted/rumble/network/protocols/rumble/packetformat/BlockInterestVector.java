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

import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;
import org.disrupted.rumble.database.objects.InterestVector;

import java.io.IOException;

/**
 * A BlockInterestVector holds information about the current node such as
 *
 * - Its User ID
 * - The list of groups ID (GID) it belongs to
 * - its interests hashtags subscription list (value, hashtag)
 *
 * +-------------------------------------------+
 * |               User ID                     | 8 byte Author UID
 * +-------+-----------------------------------+
 * | #GID  |  GID1, GID2, GID3, etc..          | 1 byte + variable (8* #GID)
 * +-------+-------+---------------------------+
 * | value |  size |   Hashtag                 | 1 byte + 1 byte + variable
 * +-------+-------+---------------------------+
 *
 * @author Marlinski
 */
public class BlockInterestVector extends Block {

    private static final String TAG = "BlockStatus";

    /*
     * Byte size
     */
    private static final int AUTHOR_ID           = 8;
    private static final int NUMBER_GID          = 1;
    private static final int GROUP_ID            = 8;
    private static final int INTEREST_VALUE      = 1;
    private static final int HASHTAG_SIZE        = 1;

    private  static final int MIN_PAYLOAD_SIZE = (
                    AUTHOR_ID +
                    NUMBER_GID
    );

    private InterestVector interestVector;

    private static final int MAX_HASHTAG_SIZE = 255;

    public BlockInterestVector() {
        super(new BlockHeader());
    }

    @Override
    public long readBlock(LinkLayerConnection con) throws MalformedRumblePacket, IOException, InputOutputStreamException {
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
