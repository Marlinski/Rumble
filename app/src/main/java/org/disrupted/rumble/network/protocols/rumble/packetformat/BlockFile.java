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

import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.network.events.FileReceivedEvent;
import org.disrupted.rumble.network.events.FileSentEvent;
import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.exception.InputOutputStreamException;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.protocols.rumble.packetformat.exceptions.MalformedRumblePacket;
import org.disrupted.rumble.util.FileUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * A BlockFile is just a binary stream of size header.length
 *
 * +-------------------------------------------+
 * |                                           |
 * |       UID = Hash(author, post, toc)       |  16 bytes (128 bits UID)
 * |                                           |
 * |                                           |
 * +-----------+-------------------------------+
 * |   MIME    |                               |  1 byte
 * +-----------+                               |
 * |                                           |
 * |       Binary .....                        |
 * |                                           |
 * |                                           |
 * +-------------------------------------------+
 *
 * @author Marlinski
 */
public class BlockFile extends Block {

    public static final String TAG = "BlockFile";

    /*
     * Byte size
     */
    private static final int UID_SIZE             = 16;
    private static final int MIME_TYPE_SIZE        = 1;

    private  static final int MIN_PAYLOAD_SIZE = ( UID_SIZE + MIME_TYPE_SIZE);

    public final static int MIME_TYPE_IMAGE = 0x01;

    public String filename;
    public String UUID;

    public BlockFile(BlockHeader header) {
        super(header);
    }

    public BlockFile(String filename, String UUID) {
        super(new BlockHeader());
        header.setBlockType(BlockHeader.BLOCKTYPE_FILE);
        this.filename = filename;
        this.UUID = UUID;
    }

    @Override
    public long readBlock(LinkLayerConnection con) throws MalformedRumblePacket, IOException, InputOutputStreamException {
        if(header.getBlockType() != BlockHeader.BLOCKTYPE_FILE)
            throw new MalformedRumblePacket("Block type BLOCK_FILE expected");

        long readleft = header.getBlockLength();
        if(readleft < 0)
            throw new MalformedRumblePacket("Header length is < 0 in BlockFile");

        long timeToTransfer = System.currentTimeMillis();

        /* read the block pseudo header */
        InputStream in = con.getInputStream();
        byte[] pseudoHeaderBuffer = new byte[MIN_PAYLOAD_SIZE];
        int count = in.read(pseudoHeaderBuffer, 0, MIN_PAYLOAD_SIZE);
        if (count < MIN_PAYLOAD_SIZE)
            throw new MalformedRumblePacket("read less bytes than expected: "+count);
        readleft -= MIN_PAYLOAD_SIZE;

        ByteBuffer byteBuffer = ByteBuffer.wrap(pseudoHeaderBuffer);
        byte[] uid = new byte[UID_SIZE];
        byteBuffer.get(uid, 0, UID_SIZE);
        readleft -= UID_SIZE;

        int mime = byteBuffer.get();
        readleft -= MIME_TYPE_SIZE;

        if((mime == MIME_TYPE_IMAGE)) {
            File directory;
            try {
                directory = FileUtil.getWritableAlbumStorageDir();
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "JPEG_" + timeStamp + "_";
                String suffix = ".jpg";
                File attachedFile = File.createTempFile(
                        imageFileName,  /* prefix */
                        suffix,         /* suffix */
                        directory       /* directory */
                );

                FileOutputStream fos = null;
                try {
                    fos = new FileOutputStream(attachedFile);
                    final int BUFFER_SIZE = 1024;
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (readleft > 0) {
                        long max_read = Math.min((long)BUFFER_SIZE,readleft);
                        int bytesread = in.read(buffer, 0, (int)max_read);
                        if (bytesread < 0)
                            throw new IOException("End of stream reached before downloading was complete");
                        readleft -= bytesread;
                        fos.write(buffer, 0, bytesread);
                    }
                } catch (IOException e) {
                    throw e;
                } finally {
                    if (fos != null)
                        fos.close();
                }

                // add the photo to the media library
                Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                Uri contentUri = Uri.fromFile(attachedFile);
                mediaScanIntent.setData(contentUri);
                RumbleApplication.getContext().sendBroadcast(mediaScanIntent);

                timeToTransfer  = (System.currentTimeMillis() - timeToTransfer);
                // update the database
                Log.e(TAG, "[+] "+(header.getBlockLength()+header.BLOCK_HEADER_LENGTH)+" received in "+(timeToTransfer/1000L)+" milliseconds");
                EventBus.getDefault().post(new FileReceivedEvent(
                                attachedFile.getName(),
                                new String(uid),
                                RumbleProtocol.protocolID,
                                con.getLinkLayerIdentifier(),
                                header.getBlockLength()+header.BLOCK_HEADER_LENGTH,
                                timeToTransfer)
                );
            } catch (IOException e) {
                Log.e(TAG, "[-] file has not been downloaded "+e.getMessage());
            }
        } else {
            int BUFFER_SIZE = 1024;
            byte[] buffer = new byte[BUFFER_SIZE];
            while (readleft > 0) {
                long max_read = Math.min((long)BUFFER_SIZE,readleft);
                int bytesread = in.read(buffer, 0, (int)max_read);
                if (bytesread < 0)
                    throw new IOException("End of stream reached before downloading was complete");
                readleft -= bytesread;
            }
        }
        return 0;
    }

    @Override
    public long writeBlock(LinkLayerConnection con) throws IOException, InputOutputStreamException {
        if(filename == null)
            throw new IOException("filename is null");

        File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), filename);
        if(!attachedFile.exists() || !attachedFile.isFile())
            throw new IOException(filename+" is not a file or does not exists");

        long timeToTransfer = System.currentTimeMillis();

        /* calculate the total block size */
        long size = attachedFile.length();
        this.header.setBlockHeaderLength(size + MIN_PAYLOAD_SIZE);

        ByteBuffer bufferBlockHeader = ByteBuffer.allocate(MIN_PAYLOAD_SIZE);
        bufferBlockHeader.put(UUID.getBytes(), 0, UID_SIZE);
        bufferBlockHeader.put((byte) MIME_TYPE_IMAGE);

    /* send the header, the pseudo-header and the attached file */
        header.writeBlock(con.getOutputStream());
        con.getOutputStream().write(bufferBlockHeader.array());

        BufferedInputStream fis = null;
        try {
            OutputStream out = con.getOutputStream();
            final int BUFFER_SIZE = 1024;
            byte[] fileBuffer = new byte[BUFFER_SIZE];
            fis = new BufferedInputStream(new FileInputStream(attachedFile));
            int bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            while (bytesread > 0) {
                out.write(fileBuffer, 0, bytesread);
                bytesread = fis.read(fileBuffer, 0, BUFFER_SIZE);
            }
        } finally {
            if (fis != null)
                fis.close();
        }

        timeToTransfer = (System.currentTimeMillis() - timeToTransfer);
        List<String> recipients = new ArrayList<String>();
        recipients.add(con.getRemoteLinkLayerAddress());
        EventBus.getDefault().post(new FileSentEvent(
                        filename,
                        recipients,
                        RumbleProtocol.protocolID,
                        BluetoothLinkLayerAdapter.LinkLayerIdentifier,
                        header.getBlockLength() + header.BLOCK_HEADER_LENGTH,
                        timeToTransfer)
        );

        return 0;
    }

    @Override
    public void dismiss() {

    }
}
