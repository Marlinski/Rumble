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

package org.disrupted.rumble.network.protocols.Rumble;

import android.util.Log;

import org.disrupted.rumble.network.protocols.Rumble.packetformat.BlockHello;
import org.disrupted.rumble.network.protocols.Rumble.packetformat.exceptions.MalformedRumblePacket;
import org.disrupted.rumble.network.protocols.command.Command;

import java.io.IOException;

/**
 * @author Marlinski
 */
public class RumbleBTClient extends org.disrupted.rumble.network.linklayer.bluetooth.BluetoothClient {

    private static final String TAG = "RumbleBluetoothClient";

    public RumbleBTClient(String remoteMacAddress){
        super(remoteMacAddress, RumbleBTConfiguration.RUMBLE_BT_UUID_128, RumbleBTConfiguration.RUMBLE_BT_STR, false);
    }

    @Override
    public String getConnectionID() {
        return "BTRumble: "+this.remoteMacAddress;
    }

    @Override
    public String getProtocolID() {
        return "Rumble";
    }

    @Override
    protected void initializeProtocol() {
        BlockHello hello = new BlockHello();
        try {
            Log.d(TAG, "[+] sending hello packet :-)");
            outputStream.write(hello.getBytes(), 0, hello.getLength());
        }
        catch(IOException e) {
            Log.e(TAG, "[!] unable to say hello");
        }
        catch(MalformedRumblePacket e) {
            Log.e(TAG, "[!] malformed packet");
        }
    }

    @Override
    protected void destroyProtocol() {
    }

    @Override
    public boolean isCommandSupported(String commandName) {
        return false;
    }

    @Override
    protected void processingPacketFromNetwork() throws IOException {
        RumbleBTReceiver receiver = new RumbleBTReceiver(this);
        receiver.run();
    }

    @Override
    protected boolean onCommandReceived(Command command) {
        return false;
    }

    @Override
    public void stop() {
        kill();
    }
}
