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

package org.disrupted.rumble.network.protocols.events;

import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.network.events.NetworkEvent;

import java.util.List;

/**
 * @author Marlinski
 */
public class ChatMessageSent extends NetworkEvent {

    public ChatMessage chatMessage;
    public List<String> recipients;
    public String protocolID;
    public String linkLayerIdentifier;
    public long size;
    public long duration;

    public ChatMessageSent(ChatMessage chatMessage, List<String> recipients, String protocolID, String linkLayerIdentifier, long size, long duration) {
        this.chatMessage = chatMessage;
        this.recipients = recipients;
        this.protocolID = protocolID;
        this.linkLayerIdentifier = linkLayerIdentifier;
        this.size = size;
        this.duration = duration;
    }

    @Override
    public String shortDescription() {
        if(chatMessage != null)
            return chatMessage.getMessage()+" ("+chatMessage.getAuthor().getName()+")";
        else
            return null;
    }
}
