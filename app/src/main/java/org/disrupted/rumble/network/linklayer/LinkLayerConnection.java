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

package org.disrupted.rumble.network.linklayer;

import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.linklayer.exception.LinkLayerConnectionException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * LinkLayerConnection is an interface for a class to implement connect() and disconnect()
 * method that are link-layer specifics.
 *
 * @author Marlinski
 */
public interface LinkLayerConnection {

    public String getLinkLayerIdentifier();

    public String getConnectionID();

    public void connect() throws LinkLayerConnectionException;

    public void disconnect() throws LinkLayerConnectionException;

    public InputStream getInputStream() throws IOException, InputOutputStreamException;

    public OutputStream getOutputStream()  throws IOException, InputOutputStreamException;

    public String getRemoteLinkLayerAddress();

    public LinkLayerNeighbour getLinkLayerNeighbour();

}
