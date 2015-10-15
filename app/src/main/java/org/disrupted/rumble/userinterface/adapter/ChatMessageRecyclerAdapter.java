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

package org.disrupted.rumble.userinterface.adapter;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.activity.DisplayImage;
import org.disrupted.rumble.userinterface.events.UserReadChatMessage;
import org.disrupted.rumble.userinterface.fragments.FragmentChatMessageList;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.TimeUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class ChatMessageRecyclerAdapter extends RecyclerView.Adapter<ChatMessageRecyclerAdapter.ChatMessageHolder> {

    public static final String TAG = "ChatMessageAdapter";

    public class ChatMessageHolder extends RecyclerView.ViewHolder {

        FrameLayout senderBox;
        ImageView senderAvatar;
        FrameLayout localBox;
        ImageView localAvatar;
        LinearLayout messageBox;
        TextView authorView;
        TextView  textView;
        TextView  dateView;
        ImageView attachedView;

        public ChatMessageHolder(View itemView) {
            super(itemView);
            senderBox   = (FrameLayout)itemView.findViewById(R.id.chat_sender_avatar_box);
            senderAvatar  = (ImageView)itemView.findViewById(R.id.chat_sender_avatar);
            localBox    = (FrameLayout)itemView.findViewById(R.id.chat_local_avatar_box);
            localAvatar   = (ImageView)itemView.findViewById(R.id.chat_local_avatar);
            messageBox = (LinearLayout)itemView.findViewById(R.id.chat_message_box);
            authorView     = (TextView)itemView.findViewById(R.id.chat_message_author);
            textView      = (TextView)itemView.findViewById(R.id.chat_message_text);
            dateView      = (TextView)itemView.findViewById(R.id.chat_message_date);
            attachedView  = (ImageView)itemView.findViewById(R.id.chat_message_attached_image);
        }

        public void bindChatMessage(ChatMessage message) {
            ImageView avatar;
            Contact author = Contact.getLocalContact();
            String receivedOrSent = "";
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if(message.getAuthor().equals(author)) {
                senderBox.setVisibility(View.INVISIBLE);
                localBox.setVisibility(View.VISIBLE);
                avatar = localAvatar;
                params.gravity = Gravity.RIGHT;
                receivedOrSent = "sent: ";
            } else {
                localBox.setVisibility(View.INVISIBLE);
                senderBox.setVisibility(View.VISIBLE);
                avatar = senderAvatar;
                author = message.getAuthor();
                params.gravity = Gravity.LEFT;
                receivedOrSent = "received: ";
            }
            messageBox.setLayoutParams(params);
            authorView.setLayoutParams(params);
            dateView.setLayoutParams(params);

            // we draw the avatar
            ColorGenerator generator = ColorGenerator.DEFAULT;
            avatar.setImageDrawable(
                    builder.build(author.getName().substring(0, 1),
                            generator.getColor(author.getUid())));

            // we draw the author field
            authorView.setText("@"+author.getName());

            // we draw the message
            if (message.getMessage().length() > 0)
                textView.setText(message.getMessage());

            //textView.setVisibility(View.GONE);
            if (message.hasAttachedFile()) {
                try {
                    File attachedFile = new File(
                            FileUtil.getReadableAlbumStorageDir(),
                            message.getAttachedFile());
                    if (!attachedFile.isFile() || !attachedFile.exists())
                        throw new IOException("file does not exists");

                    Picasso.with(activity)
                            .load("file://" + attachedFile.getAbsolutePath())
                            .resize(96, 96)
                            .centerCrop()
                            .into(attachedView);

                    final String name = message.getAttachedFile();
                    attachedView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Log.d(TAG, "trying to open: " + name);
                            Intent intent = new Intent(activity, DisplayImage.class);
                            intent.putExtra("IMAGE_NAME", name);
                            activity.startActivity(intent);
                        }
                    });
                    attachedView.setVisibility(View.VISIBLE);
                } catch (IOException ignore) {
                }
            } else {
                attachedView.setVisibility(View.GONE);
            }

            dateView.setText(receivedOrSent+ TimeUtil.timeElapsed(message.getTimestamp()));

            if(!message.hasUserReadAlready())
                EventBus.getDefault().post(new UserReadChatMessage(message));
        }
    }

    private Activity activity;
    private List<ChatMessage> messages;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();

    public ChatMessageRecyclerAdapter(Activity activity, FragmentChatMessageList fragment) {
        this.activity = activity;
        this.messages = new ArrayList<ChatMessage>();
    }

    @Override
    public ChatMessageHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout layout = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.chatmessage_list_item, parent, false);
        return new ChatMessageHolder(layout);
    }


    @Override
    public void onBindViewHolder(ChatMessageHolder chatMessageHolder, int i) {
        ChatMessage message = messages.get(i);
        chatMessageHolder.bindChatMessage(message);
    }

    @Override
    public long getItemId(int position) {
        return messages.get(position).getUUID().hashCode();
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public int addChatMessage(ChatMessage message) {
        messages.add(0,message);
        return (0);
    }

    public void swap(List<ChatMessage> chatMessageList) {
        if(this.messages != null)
            this.messages.clear();

        if(chatMessageList != null) {
            for (ChatMessage message : chatMessageList) {
                this.messages.add(message);
            }
        }
    }

    public void clean() {
        swap(null);
        activity = null;
    }

}
