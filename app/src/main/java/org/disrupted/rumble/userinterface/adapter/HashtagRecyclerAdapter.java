/*
 * Copyright (C) 2014 Lucien Loiseau
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.activity.GroupDetailActivity;
import org.disrupted.rumble.userinterface.activity.HashtagDetailActivity;
import org.disrupted.rumble.userinterface.events.UserSetHashTagInterest;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class HashtagRecyclerAdapter extends RecyclerView.Adapter<HashtagRecyclerAdapter.HashtagHolder> {

    public static final String TAG = "HashtagRecyclerAdapter";

    public class HashtagHolder extends RecyclerView.ViewHolder {

        TextView hashtag;
        TextView subscription;

        public HashtagHolder(View itemView) {
            super(itemView);
            hashtag       = (TextView) itemView.findViewById(R.id.hashtag);
            subscription  = (TextView) itemView.findViewById(R.id.subscription_button);
        }

        public void bindHashtag(final String hashtag) {
            this.hashtag.setText(hashtag);
            Integer interest = Contact.getLocalContact().getHashtagInterests().get(hashtag);
            final boolean isInterested = ((interest != null) && (interest > 0));
            if(isInterested) {
                subscription.setText(R.string.filter_subscribed);
                subscription.setTextColor(activity.getResources().getColor(R.color.white));
                subscription.setBackgroundColor(activity.getResources().getColor(R.color.green));
            } else {
                subscription.setText(R.string.filter_not_subscribed);
                subscription.setTextColor(activity.getResources().getColor(R.color.black));
                subscription.setBackgroundColor(activity.getResources().getColor(R.color.white));
            }

            subscription.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(isInterested)
                        EventBus.getDefault().post(new UserSetHashTagInterest(hashtag, Contact.MIN_INTEREST_TAG_VALUE));
                    else
                        EventBus.getDefault().post(new UserSetHashTagInterest(hashtag, Contact.MAX_INTEREST_TAG_VALUE));
                }
            });

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent hashtagStatusActivity = new Intent(activity, HashtagDetailActivity.class );
                    hashtagStatusActivity.putExtra("Hashtag",hashtag);
                    activity.startActivity(hashtagStatusActivity);
                    activity.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                }
            });
        }
    }

    private Activity activity;
    private ArrayList<String> hashtagList;

    public HashtagRecyclerAdapter(Activity activity) {
        this.activity = activity;
        this.hashtagList = null;
    }

    @Override
    public HashtagHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout layout = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hashtag_list, parent, false);
        return new HashtagHolder(layout);
    }

    @Override
    public void onBindViewHolder(HashtagHolder hashtagHolder, int i) {
        String hashtag = hashtagList.get(i);
        hashtagHolder.bindHashtag(hashtag);
    }

    @Override
    public int getItemCount() {
        if(hashtagList == null)
            return 0;
        else
            return hashtagList.size();
    }

    public void swap(ArrayList<String> hashtagList) {
        if(this.hashtagList != null)
            this.hashtagList.clear();
        this.hashtagList = hashtagList;
        notifyDataSetChanged();
    }
}
