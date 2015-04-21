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
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
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
import org.disrupted.rumble.userinterface.fragments.FragmentChatMessage;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class ChatMessageListAdapter extends BaseAdapter {


    private static final String TAG = "StatusListAdapter";

    private FragmentChatMessage fragment;
    private Activity activity;
    private LayoutInflater inflater;
    private List<ChatMessage> chatMessageList;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();


    public ChatMessageListAdapter(Activity activity, FragmentChatMessage fragment) {
        this.activity = activity;
        this.fragment = fragment;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.chatMessageList = new ArrayList<ChatMessage>();
    }

    public void clean() {
        swap(null);
        inflater = null;
        activity = null;
        fragment = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {

        final ChatMessage message = chatMessageList.get(i);

        View chatMessageView = inflater.inflate(R.layout.chat_message_item, null);
        FrameLayout senderBox   =  (FrameLayout) chatMessageView.findViewById(R.id.chat_sender_avatar_box);
        ImageView senderAvatar  = (ImageView)chatMessageView.findViewById(R.id.chat_sender_avatar);
        FrameLayout localBox    =  (FrameLayout) chatMessageView.findViewById(R.id.chat_local_avatar_box);
        ImageView localAvatar   = (ImageView)chatMessageView.findViewById(R.id.chat_local_avatar);
        LinearLayout messageBox = (LinearLayout)chatMessageView.findViewById(R.id.chat_message_box);
        TextView  authorView    = (TextView) chatMessageView.findViewById(R.id.chat_message_author);
        TextView  textView      = (TextView) chatMessageView.findViewById(R.id.chat_message_text);
        TextView  dateView      = (TextView)chatMessageView.findViewById(R.id.chat_message_date);
        ImageView attachedView  = (ImageView)chatMessageView.findViewById(R.id.chat_message_attached_image);

        ImageView avatar;
        Contact author = Contact.getLocalContact();
        String receivedOrSent = "";
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if(message.getAuthor().equals(author)) {
            senderBox.setVisibility(View.INVISIBLE);
            avatar = localAvatar;
            params.gravity = Gravity.RIGHT;
            receivedOrSent = "sent: ";
        } else {
            localBox.setVisibility(View.INVISIBLE);
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

        // we draw the status (with clickable hashtag)
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
        }

        dateView.setText(receivedOrSent+new TimeElapsed(message.getTimestamp()).display());

        if(!message.hasUserReadAlready())
            EventBus.getDefault().post(new UserReadChatMessage(message));

        return chatMessageView;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        if(chatMessageList == null)
            return 0;
        return i;
    }

    @Override
    public int getCount() {
        if(chatMessageList == null)
            return 0;
        return chatMessageList.size();
    }

    public void swap(List<ChatMessage> chatMessageList) {
        if(this.chatMessageList != null)
            this.chatMessageList.clear();

        if(chatMessageList != null) {
            for (ChatMessage message : chatMessageList) {
                this.chatMessageList.add(message);
            }
        }
    }

    public boolean addChatMessage(ChatMessage status) {
        List<ChatMessage> newlist = new ArrayList<ChatMessage>();
        newlist.add(status);
        for (ChatMessage item : chatMessageList) {
            newlist.add(item);
        }
        swap(newlist);
        return true;
    }

    private class TimeElapsed {

        private static final long ONE_MINUTE_IN_SECONDS = 60;
        private static final long ONE_HOUR_IN_SECONDS = 60 * ONE_MINUTE_IN_SECONDS;
        private static final long ONE_DAY_IN_SECONDS = 24 * ONE_HOUR_IN_SECONDS;
        private static final long ONE_MONTH_IN_SECONDS = 30 * ONE_DAY_IN_SECONDS;
        private static final long ONE_YEAR_IN_SECONDS = 365 * ONE_DAY_IN_SECONDS;

        private long time;

        TimeElapsed(long timeInSecondsSinceEpoch) {
            this.time = (System.currentTimeMillis() / 1000L) - timeInSecondsSinceEpoch;
        }

        public String display() {
            Resources res = activity.getResources();
            if(time < ONE_MINUTE_IN_SECONDS)
                return getTimeInSeconds()+" "+res.getString(R.string.seconds_ago);

            if(time < 2*ONE_MINUTE_IN_SECONDS)
                return res.getString(R.string.minute_ago);
            if(time < ONE_HOUR_IN_SECONDS)
                return getTimeInMinutes()+" "+res.getString(R.string.minutes_ago);

            if(time < 2*ONE_HOUR_IN_SECONDS)
                return res.getString(R.string.hour_ago);
            if(time < ONE_DAY_IN_SECONDS)
                return getTimeInHours()+" "+res.getString(R.string.hours_ago);

            if(time < 2*ONE_DAY_IN_SECONDS)
                return res.getString(R.string.day_ago);
            if(time < ONE_MONTH_IN_SECONDS)
                return getTimeInDays()+" "+res.getString(R.string.days_ago);

            if(time < 2*ONE_MONTH_IN_SECONDS)
                return res.getString(R.string.month_ago);
            if(time < ONE_YEAR_IN_SECONDS)
                return getTimeInMonths()+" "+res.getString(R.string.months_ago);

            if(time < 2*ONE_YEAR_IN_SECONDS)
                return res.getString(R.string.year_ago);
            if(time < 10*ONE_YEAR_IN_SECONDS)
                return getTimeInYears()+" "+res.getString(R.string.years_ago);

            return res.getString(R.string.too_old);
        }

        private String getTimeInSeconds(){
            return Long.toString(time);
        }
        private String getTimeInMinutes(){
            return Long.toString(time/ONE_MINUTE_IN_SECONDS);
        }
        private String getTimeInHours(){
            return Long.toString(time/ONE_HOUR_IN_SECONDS);
        }
        private String getTimeInDays(){
            return Long.toString(time/ONE_DAY_IN_SECONDS);
        }
        private String getTimeInMonths(){
            return Long.toString(time/ONE_MONTH_IN_SECONDS);
        }
        private String getTimeInYears(){
            return Long.toString(time/ONE_YEAR_IN_SECONDS);
        }

    }
}
