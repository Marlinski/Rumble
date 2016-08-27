/*
 * Copyright (C) 2014 Lucien Loiseau
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

package org.disrupted.rumble.database;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.disrupted.rumble.userinterface.events.UserLeaveGroup;
import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.events.ChatMessageInsertedEvent;
import org.disrupted.rumble.database.events.ChatMessageUpdatedEvent;
import org.disrupted.rumble.database.events.ContactGroupListUpdated;
import org.disrupted.rumble.database.events.ContactInterfaceInserted;
import org.disrupted.rumble.database.events.ContactTagInterestUpdatedEvent;
import org.disrupted.rumble.database.events.FileInsertedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusDuplicate;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.events.ContactConnected;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.network.protocols.events.ChatMessageSent;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.events.PushStatusReceived;
import org.disrupted.rumble.network.protocols.events.PushStatusSent;
import org.disrupted.rumble.userinterface.events.UserComposeChatMessage;
import org.disrupted.rumble.userinterface.events.UserComposeStatus;
import org.disrupted.rumble.userinterface.events.UserCreateGroup;
import org.disrupted.rumble.userinterface.events.UserDeleteGroup;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadChatMessage;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.userinterface.events.UserSetHashTagInterest;
import org.disrupted.rumble.userinterface.events.UserWipeChatMessages;
import org.disrupted.rumble.userinterface.events.UserWipeData;
import org.disrupted.rumble.userinterface.events.UserWipeFiles;
import org.disrupted.rumble.userinterface.events.UserWipeStatuses;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.NetUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import de.greenrobot.event.EventBus;

/**
 * The CacheManager takes care of updating the database accordingly to the catched event
 *
 * @author Lucien Loiseau
 */
public class CacheManager {

    private static final String TAG = "CacheManager";

    private static final int MAX_IMAGE_SIZE_ON_DISK = 500000;
    private static final int MAX_IMAGE_BORDER_PX = 1000;

    private static final Object globalQueuelock = new Object();
    private static CacheManager instance;

    private boolean started;

    public static CacheManager getInstance() {
        synchronized (globalQueuelock) {
            if (instance == null)
                instance = new CacheManager();

            return instance;
        }
    }

    public void start() {
        if(!started) {
            Log.d(TAG, "[+] Starting Cache Manager");
            started = true;
            EventBus.getDefault().register(this);
        }
    }

    public void stop() {
        if(started) {
            Log.d(TAG, "[-] Stopping Cache Manager");
            started = false;
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }
    }


    /*
     * Managing Network Interaction, onEventAsync to avoid slowing down network
     */
    public void onEventAsync(PushStatusReceived event) {
        try {
            if (event.status == null)
                return;
            if ((event.status.getAuthor() == null) || (event.status.getGroup() == null) || (event.status.receivedBy() == null))
                return;

            Group group = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).getGroup(event.gid);
            if(group == null) {
                // we do not belong to the group
                Log.d(TAG, "[!] unknow group: refusing the message");
                return;
            }
            event.status.setGroup(group);

            Contact sender = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContact(event.status.receivedBy());
            if (sender == null) {
                // we do not accept message from unknown sender, that should never happen as the protocol starts by exchange
                // ContactInformation blocks
                Log.d(TAG, "[!] unknown sender: refusing the message");
                return;
            }

            // we update the sender statistics
            sender.setStatusReceived(sender.nbStatusReceived() + 1);
            DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(sender);

            // we insert/update the status author
            Contact author = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContact(event.status.getAuthor().getUid());
            if (author == null) {
                author = event.status.getAuthor();
                DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(author);
            } else if (!author.getName().equals(event.status.getAuthor().getName())) {
                // we do not accept message if the author has changed since we last known of (UID/name)
                Log.d(TAG, "[!] AuthorID: " + author.getUid() + " CONFLICT: db=" + author.getName() + " status=" + event.status.getAuthor().getName());
                return;
            }
            // we add the author to the group if it doesn't already belong
            long authorDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContactDBID(author.getUid());
            long groupDBID = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).getGroupDBID(event.status.getGroup().getGid());
            if (DatabaseFactory.getContactJoinGroupDatabase(RumbleApplication.getContext()).insertContactGroup(authorDBID, groupDBID) >= 0)
                EventBus.getDefault().post(new ContactGroupListUpdated(author));

            // we add the status to the database
            PushStatus exists = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(event.status.getUuid());
            if (exists == null) {
                exists = new PushStatus(event.status);
                exists.addDuplicate(1);
                DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).insertStatus(exists);
            } else {
                exists.addDuplicate(1);
                if (event.status.getLike() > 0)
                    exists.addLike();
                DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(exists);
                EventBus.getDefault().post(new StatusDuplicate());
            }

            // then the StatusContact database
            if (exists.getdbId() > 0) {
                long senderDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContactDBID(event.senderID);
                if (senderDBID > 0)
                    DatabaseFactory.getStatusContactDatabase(RumbleApplication.getContext()).insertStatusContact(exists.getdbId(), senderDBID);
            }

            // now we take care of the attached file, if any
            if (event.tempfile.equals(""))
                return;

            if (!FileUtil.isFileNameClean(exists.getFileName()))
                throw new Exception("filename is suspicious");

            /*
             * we check if we already received the attached file. We don't want to overwrite as
             * it would enable an attack that would overwrite every status
             */
            File attached = new File(FileUtil.getWritableAlbumStorageDir(), exists.getFileName());
            if (attached.exists())
                throw new Exception("file already exists");

            if (saveImageOnDisk(event.tempfile, exists.getFileName()))
                EventBus.getDefault().post(new FileInsertedEvent(attached.getName(), exists.getUuid()));

        } catch (Exception ignore ) {
            ignore.getMessage();
        } finally {
            if (!event.tempfile.equals("")) {
                try {
                    File toDelete = new File(FileUtil.getWritableAlbumStorageDir(), event.tempfile);
                    toDelete.delete();
                } catch (IOException ignore) {
                }
            }
            if(event.status != null)
                event.status.discard();
        }
    }
    public void onEventAsync(ContactInformationReceived event) {
        Contact contact = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContact(event.contact.getUid());
        if(contact == null) {
            contact = new Contact(event.contact);
            // first time we meet this fellow, we add it to the database
            DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(contact);
        } else {
            // we update the contact information only under certain conditions
            if((contact.isFriend()) && (!event.authenticated)) {
                // we do not accept unauthenticated update from friends
                Log.d(TAG, "[!] receive contact update for a friend but content was not authenticated");
                return;
            }
            if(!contact.getName().equals(event.contact.getName())) {
                // we do not accept conflicting name/uid, it means the packet was forged
                Log.d(TAG, "[!] AuthorID: "+contact.getUid()+ " CONFLICT: db="+contact.getName()+" status="+event.contact.getName());
                return;
            }
            if(contact.isLocal()) {
                // of course, we do not accept receiving update for our own self
                Log.d(TAG, "[!] receive contact information for ourself");
                return;
            }

            contact.lastMet(event.contact.lastMet());
            DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(contact);
        }

        long contactDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                .getContactDBID(contact.getUid());

        // We only update the affected attributes
        if ((event.flags & Contact.FLAG_GROUP_LIST) == Contact.FLAG_GROUP_LIST) {
            contact.setJoinedGroupIDs(event.contact.getJoinedGroupIDs());
            DatabaseFactory.getContactJoinGroupDatabase(RumbleApplication.getContext())
                    .deleteEntriesMatchingContactID(contactDBID);
            for(String group : contact.getJoinedGroupIDs()) {
                long groupDBID   = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).getGroupDBID(group);
                if(groupDBID > 0)
                    DatabaseFactory.getContactJoinGroupDatabase(RumbleApplication.getContext()).insertContactGroup(contactDBID, groupDBID);
            }
            EventBus.getDefault().post(new ContactGroupListUpdated(contact));
        }
        if ((event.flags & Contact.FLAG_TAG_INTEREST) == Contact.FLAG_TAG_INTEREST) {
            contact.setHashtagInterests(event.contact.getHashtagInterests());
            DatabaseFactory.getContactHashTagInterestDatabase(RumbleApplication.getContext())
                    .deleteEntriesMatchingContactID(contactDBID);
            for(Map.Entry<String, Integer> entry : contact.getHashtagInterests().entrySet()) {
                long hashtagDBID  = DatabaseFactory.getHashtagDatabase(RumbleApplication.getContext()).getHashtagDBID(entry.getKey());
                if(hashtagDBID > 0)
                    DatabaseFactory.getContactHashTagInterestDatabase(RumbleApplication.getContext()).insertContactTagInterest(contactDBID, hashtagDBID, entry.getValue());
            }
            EventBus.getDefault().post(new ContactTagInterestUpdatedEvent(contact));
        }

        // We also keep track of the interface and protocol this contact was discovered on
        try {
            long interfaceDBID = DatabaseFactory.getInterfaceDatabase(RumbleApplication.getContext())
                    .insertInterface(
                            event.neighbour.getLinkLayerMacAddress(),
                            event.channel.getProtocolIdentifier());
            long res = DatabaseFactory.getContactInterfaceDatabase(RumbleApplication.getContext())
                    .insertContactInterface(contactDBID, interfaceDBID);
            if (res > 0)
                EventBus.getDefault().post(new ContactInterfaceInserted(contact, event.neighbour, event.channel));
        } catch(NetUtil.NoMacAddressException ignore) {
        }
    }
    public void onEventAsync(PushStatusSent event) {
        if(event.status == null)
            return;

        PushStatus status = new PushStatus(event.status);
        status.addReplication(event.recipients.size());

        // first we update the status
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(status);

        // then the Contact database
        if(status.getdbId() > 0) {
            for(Contact recipient : event.recipients) {
                Contact contact = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContact(recipient.getUid());
                long contactDBID;
                if(contact == null) {
                    recipient.setStatusSent(1);
                    contactDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(recipient);
                } else {
                    contact.setStatusSent(contact.nbStatusSent()+1);
                    contactDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(contact);
                }
                DatabaseFactory.getStatusContactDatabase(RumbleApplication.getContext()).insertStatusContact(status.getdbId(), contactDBID);
            }
        }
    }
    public void onEventAsync(ChatMessageReceived event) {
        if(event.chatMessage == null)
            return;

        long existsDBID = DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).getChatMessageDBID(event.chatMessage.getUUID());
        if(existsDBID > 0)
            return;

        // we insert/update the contact to the database
        Contact contact = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContact(event.chatMessage.getAuthor().getUid());
        if(contact == null) {
            contact = event.chatMessage.getAuthor();
            DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).insertOrUpdateContact(contact);
        } else if(!contact.getName().equals(event.chatMessage.getAuthor().getName())) {
            // we do not accept message for which the author has changed since we last known of (UID/name)
            Log.d(TAG, "[!] AuthorID: "+contact.getUid()+ " CONFLICT: db="+contact.getName()+" status="+event.chatMessage.getAuthor().getName());
            return;
        }
        ChatMessage chatMessage = new ChatMessage(event.chatMessage);
        if(DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).insertMessage(chatMessage) > 0);
            EventBus.getDefault().post(new ChatMessageInsertedEvent(chatMessage, event.channel));
    }
    public void onEventAsync(ChatMessageSent event) {
        if(event.chatMessage == null)
            return;

        ChatMessage chatMessage = DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).getChatMessage(event.chatMessage.getUUID());
        if(chatMessage == null)
            return;

        chatMessage.setNbRecipients(chatMessage.getNbRecipients()+1);
        DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).updateMessage(chatMessage);
        EventBus.getDefault().post(new ChatMessageUpdatedEvent(chatMessage));
    }

    /*
     * Managing User Interaction, onEventAsync to avoid slowing down UI
     */
    public void onEventAsync(UserSetHashTagInterest event) {
        if(event.hashtag == null)
            return;
        Contact contact = Contact.getLocalContact();
        long contactDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContactDBID(contact.getUid());
        long tagDBID     = DatabaseFactory.getHashtagDatabase(RumbleApplication.getContext()).getHashtagDBID(event.hashtag);
        if((event.levelOfInterest == 0) && contact.getHashtagInterests().containsKey(event.hashtag)) {
            contact.getHashtagInterests().remove(event.hashtag);
            DatabaseFactory.getContactHashTagInterestDatabase(RumbleApplication.getContext()).deleteContactTagInterest(contactDBID, tagDBID);
        } else {
            contact.getHashtagInterests().put(event.hashtag, event.levelOfInterest);
            DatabaseFactory.getContactHashTagInterestDatabase(RumbleApplication.getContext()).insertContactTagInterest(contactDBID, tagDBID, event.levelOfInterest);
        }
        EventBus.getDefault().post(new ContactTagInterestUpdatedEvent(contact));

    }
    public void onEventAsync(UserReadStatus event) {
        if(event.status == null)
            return;
        event.status.setUserRead(true);
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status);
        //todo trow an event
    }
    public void onEventAsync(UserLikedStatus event) {
        if(event.status == null)
            return;
        event.status.setUserLike(true);
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status);
        //todo trow an event
    }
    public void onEventAsync(UserSavedStatus event) {
        if(event.status == null)
            return;
        event.status.setUserSaved(true);
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).updateStatus(event.status);
        //todo trow an event
    }
    public void onEventAsync(UserDeleteStatus event) {
        if(event.status == null)
            return;
        if(DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).deleteStatus(event.status.getUuid())) {
            if(event.status.hasAttachedFile()) {
                // if filename starts with a '/' it means that the file is from another album
                // we do not delete the file in that case
                if(!event.status.getFileName().startsWith("/")) {
                    try {
                        File attached = new File(FileUtil.getWritableAlbumStorageDir(), event.status.getFileName());
                        if (attached.exists() && attached.isFile()) {
                            attached.delete();
                        }
                    } catch (IOException e) {
                    }
                }
            }
            EventBus.getDefault().post(new StatusDeletedEvent(event.status.getUuid(), event.status.getdbId()));
        }
    }
    public void onEventAsync(UserComposeStatus event) {
        try {
            if(event.status == null)
                return;
            if(!event.tempfile.equals("")) {
                String cleanedUuid = FileUtil.cleanBase64(event.status.getUuid());
                String filename = "JPEG_" + cleanedUuid + ".jpg";
                if (saveImageOnDisk(event.tempfile, filename))
                    event.status.setFileName(filename);
            }
            PushStatus status = new PushStatus(event.status);
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).insertStatus(status);

            // we subscribe the user to every hashtag he used in his message
            if(status.getHashtagSet().size() > 0) {
                for(String hashtag : status.getHashtagSet()) {
                    onEventAsync(new UserSetHashTagInterest(hashtag,255));
                }
            }
        } finally {
            if (!event.tempfile.equals("")) {
                try {
                    File toDelete = new File(FileUtil.getWritableAlbumStorageDir(), event.tempfile);
                    toDelete.delete();
                } catch (IOException ignore) {
                }
            }
            if(event.status != null)
                event.status.discard();
        }
    }
    public void onEventAsync(UserCreateGroup event) {
        if(event.group == null)
            return;
        if(DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).insertGroup(event.group)) {
            Contact local = Contact.getLocalContact();
            local.addGroup(event.group.getGid());
            long contactDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContactDBID(local.getUid());
            long groupDBID = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).getGroupDBID(event.group.getGid());
            DatabaseFactory.getContactJoinGroupDatabase(RumbleApplication.getContext()).insertContactGroup(contactDBID, groupDBID);
            EventBus.getDefault().post(new ContactGroupListUpdated(local));
        }
    }
    public void onEventAsync(UserJoinGroup event) {
        if(event.group == null)
            return;
        if(DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).insertGroup(event.group)) {
            Contact local = Contact.getLocalContact();
            local.addGroup(event.group.getGid());
            long contactDBID = DatabaseFactory.getContactDatabase(RumbleApplication.getContext()).getContactDBID(local.getUid());
            long groupDBID = DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).getGroupDBID(event.group.getGid());
            DatabaseFactory.getContactJoinGroupDatabase(RumbleApplication.getContext()).insertContactGroup(contactDBID,groupDBID);
            EventBus.getDefault().post(new ContactGroupListUpdated(local));
        }
    }
    public void onEventAsync(UserDeleteGroup event) {
        if(event.gid == null)
            return;
        DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).deleteGroupStatus(event.gid);
    }
    public void onEventAsync(UserLeaveGroup event) {
        if(event.gid == null)
            return;
        DatabaseFactory.getGroupDatabase(RumbleApplication.getContext()).leaveGroup(event.gid);
    }
    public void onEventAsync(UserWipeStatuses event) {
        DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).wipe();
    }
    public void onEventAsync(UserComposeChatMessage event) {
        if (event.chatMessage == null)
            return;
        ChatMessage chatMessage = new ChatMessage(event.chatMessage);
        if (DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).insertMessage(chatMessage) > 0)
            EventBus.getDefault().post(new ChatMessageInsertedEvent(chatMessage));
    }
    public void onEventAsync(UserReadChatMessage event) {
        if(event.chatMessage == null)
            return;
        event.chatMessage.setUserRead(true);
        if(DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).updateMessage(event.chatMessage) > 0)
            EventBus.getDefault().post(new ChatMessageUpdatedEvent(event.chatMessage));
    }
    public void onEventAsync(UserWipeChatMessages event) {
        DatabaseFactory.getChatMessageDatabase(RumbleApplication.getContext()).wipe();
    }
    public void onEventAsync(UserWipeFiles event) {
        try {
            File dir = FileUtil.getReadableAlbumStorageDir();
            if(dir != null) {
                File[] files = dir.listFiles();
                if(files != null) {
                    for (int i = 0; i < files.length; i++) {
                        files[i].delete();
                    }
                }
            }
        }catch(IOException ie){}
    }
    public void onEventAsync(UserWipeData event) {
        this.onEventAsync(new UserWipeStatuses());
        this.onEventAsync(new UserWipeChatMessages());
        this.onEventAsync(new UserWipeFiles());
    }

    private boolean saveImageOnDisk(String from, String to) {
        File toDelete = null;
        File savedImage = null;
        try {
            toDelete = new File(FileUtil.getWritableAlbumStorageDir(), from);
            savedImage   = new File(FileUtil.getWritableAlbumStorageDir(), to);
            if(toDelete.exists() && toDelete.isFile() && (toDelete.length() > MAX_IMAGE_SIZE_ON_DISK)) {

                // first we extrat the width and height of the image
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(toDelete.getAbsolutePath(), options);
                final int height = options.outHeight;
                final int width = options.outWidth;

                Bitmap scaled;
                if((height > MAX_IMAGE_BORDER_PX) || (height > MAX_IMAGE_BORDER_PX)) {
                    // we compute the new required height and width given that
                    // we want the bigger border (Width or Height) to be 1000 px
                    float factor = Math.max(height, width) / (float) MAX_IMAGE_BORDER_PX;
                    int reqHeight = (int) Math.floor(height / factor);
                    int reqWidth = (int) Math.floor(width / factor);

                    Log.d(TAG, "bitmap (" + height + "," + width + ") with samplesize of" + Math.floor(factor));

                    // we can create the new bitmap
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = (int) Math.floor(factor);
                    options.inPurgeable = true;
                    Bitmap original = BitmapFactory.decodeFile(toDelete.getAbsolutePath(), options);
                    scaled = Bitmap.createScaledBitmap(original, reqWidth, reqHeight, false);
                    original.recycle();
                } else {
                    // doesn't need to be scaled down
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = 1;
                    options.inPurgeable = true;
                    scaled = BitmapFactory.decodeFile(toDelete.getAbsolutePath(), options);
                }

                // we compress 80% JPEG quality and save it to the file
                OutputStream outputStream = new FileOutputStream(savedImage);
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                outputStream.close();

                scaled.recycle();
            } else {
                // we rename the file
                File fromFile = new File(FileUtil.getWritableAlbumStorageDir(), from);
                if(!fromFile.renameTo(savedImage))
                    throw new IOException("cannot rename the file");
            }

            // we add the saved image to the library
            Uri contentUri = Uri.fromFile(savedImage);
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            mediaScanIntent.setData(contentUri);
            RumbleApplication.getContext().sendBroadcast(mediaScanIntent);

            return true;
        } catch(IOException e) {
            Log.d(TAG, "there was an error resizing the image: "+e.getMessage());
        }
        return false;
    }
}
