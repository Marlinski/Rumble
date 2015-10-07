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
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.squareup.picasso.Picasso;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.userinterface.activity.ContactDetailActivity;
import org.disrupted.rumble.userinterface.activity.DisplayImage;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.TimeUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatusRecyclerAdapter extends RecyclerView.Adapter<StatusRecyclerAdapter.StatusHolder> {

    public static final String TAG = "StatusRecyclerAdapter";

    public class StatusHolder extends RecyclerView.ViewHolder {

        ImageView avatarView;
        TextView  authorView;
        TextView  textView;
        TextView  tocView;
        TextView  toaView;
        TextView  groupNameView;
        ImageView attachedView;
        ImageView moreView;
        LinearLayout box;

        public StatusHolder(View itemView) {
            super(itemView);
            avatarView    = (ImageView)itemView.findViewById(R.id.status_item_avatar);
            authorView    = (TextView) itemView.findViewById(R.id.status_item_author);
            textView      = (TextView) itemView.findViewById(R.id.status_item_body);
            tocView       = (TextView) itemView.findViewById(R.id.status_item_created);
            toaView       = (TextView) itemView.findViewById(R.id.status_item_received);
            groupNameView = (TextView) itemView.findViewById(R.id.status_item_group_name);
            attachedView  = (ImageView)itemView.findViewById(R.id.status_item_attached_image);
            moreView      = (ImageView)itemView.findViewById(R.id.status_item_more_options);
            box           = (LinearLayout)itemView.findViewById(R.id.status_item_box);
        }

        public void bindStatus(PushStatus status) {
            final String uid = status.getAuthor().getUid();
            final String name= status.getAuthor().getName();

            // we draw the avatar
            ColorGenerator generator = ColorGenerator.DEFAULT;
            avatarView.setImageDrawable(
                    builder.build(status.getAuthor().getName().substring(0, 1),
                            generator.getColor(status.getAuthor().getUid())));
            avatarView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent contactDetailActivity = new Intent(activity, ContactDetailActivity.class );
                    contactDetailActivity.putExtra("ContactID",  uid);
                    contactDetailActivity.putExtra("ContactName",name);
                    activity.startActivity(contactDetailActivity);
                    activity.overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                }
            });

            // we draw the author field
            authorView.setText(status.getAuthor().getName());
            tocView.setText(TimeUtil.timeElapsed(status.getTimeOfCreation()));
            toaView.setText(TimeUtil.timeElapsed(status.getTimeOfArrival()));
            groupNameView.setText(status.getGroup().getName());
            groupNameView.setTextColor(generator.getColor(status.getGroup().getGid()));

            // we draw the status (with clickable hashtag)
            if (status.getPost().length() == 0) {
                itemView.setVisibility(View.GONE);
            } else {
                SpannableString ss = new SpannableString(status.getPost());
                int beginCharPosition = -1;
                int j;
                for (j = 0; j < status.getPost().length(); j++) {
                    if (status.getPost().charAt(j) == '#')
                        beginCharPosition = j;
                    if ((status.getPost().charAt(j) == ' ') && (beginCharPosition >= 0)) {
                        final String word = status.getPost().substring(beginCharPosition, j);
                        ClickableSpan clickableSpan = new ClickableSpan() {
                            @Override
                            public void onClick(View textView) {
                                fragment.addFilter(word);
                            }
                        };
                        ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        beginCharPosition = -1;
                    }
                }
                if (beginCharPosition >= 0) {
                    final String word = status.getPost().substring(beginCharPosition, j);
                    ClickableSpan clickableSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View textView) {
                            fragment.addFilter(word);
                        }
                    };
                    ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                textView.setText(ss);
                textView.setMovementMethod(LinkMovementMethod.getInstance());

                /* we draw the attached file (if any) */
                if (status.hasAttachedFile()) {
                    try {
                        File attachedFile = new File(
                                FileUtil.getReadableAlbumStorageDir(),
                                status.getFileName());
                        if (!attachedFile.isFile() || !attachedFile.exists())
                            throw new IOException("file does not exists");

                        Picasso.with(activity)
                                .load("file://"+attachedFile.getAbsolutePath())
                                .resize(96, 96)
                                .centerCrop()
                                .into(attachedView);

                        attachedView.setVisibility(View.VISIBLE);

                        final String filename =  status.getFileName();
                        attachedView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Log.d(TAG, "trying to open: " + filename);
                                Intent intent = new Intent(activity, DisplayImage.class);
                                intent.putExtra("IMAGE_NAME", filename);
                                activity.startActivity(intent);
                            }
                        });
                    } catch (IOException ignore) {
                    }
                } else {
                    attachedView.setVisibility(View.GONE);
                }

                moreView.setOnClickListener(new PopupMenuListener());
                if (!status.hasUserReadAlready() || (((System.currentTimeMillis() / 1000L) - status.getTimeOfArrival()) < 60)) {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        box.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.status_shape_unread));
                    } else {
                        box.setBackground(activity.getResources().getDrawable(R.drawable.status_shape_unread));
                    }
                    if (!status.hasUserReadAlready()) {
                        status.setUserRead(true);
                        EventBus.getDefault().post(new UserReadStatus(status));
                    }
                } else {
                    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                        box.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.status_shape_read));
                    } else {
                        box.setBackground(activity.getResources().getDrawable(R.drawable.status_shape_read));
                    }
                }
            }
        }

        private class PopupMenuListener implements View.OnClickListener
        {
            @Override
            public void onClick(View v)
            {
                PopupMenu popupMenu =  new PopupMenu(activity, v);
                popupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.status_more_option_like);
                popupMenu.getMenu().add(Menu.NONE, 2, Menu.NONE, R.string.status_more_option_save);
                popupMenu.getMenu().add(Menu.NONE, 3, Menu.NONE, R.string.status_more_option_delete);
                popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem menuItem) {
                        int pos = getAdapterPosition();
                        switch (menuItem.getItemId()) {
                            case 1:
                                EventBus.getDefault().post(new UserLikedStatus(statuses.get(pos)));
                                return true;
                            case 2:
                                EventBus.getDefault().post(new UserSavedStatus(statuses.get(pos)));
                                return true;
                            case 3:
                                EventBus.getDefault().post(new UserDeleteStatus(statuses.get(pos)));
                                return true;
                            default:
                                return false;
                        }
                    }
                });
                popupMenu.show();
            }

        }

    }

    private FragmentStatusList fragment;
    private Activity activity;
    private List<PushStatus> statuses;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();


    public StatusRecyclerAdapter(Activity activity, FragmentStatusList fragment) {
        this.activity = activity;
        this.fragment = fragment;
        this.statuses = new ArrayList<PushStatus>();
    }


    @Override
    public StatusRecyclerAdapter.StatusHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout layout = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.status_list_item, parent, false);
        return new StatusHolder(layout);
    }

    @Override
    public void onBindViewHolder(StatusHolder contactHolder, int i) {
        PushStatus status = statuses.get(i);
        contactHolder.bindStatus(status);
    }

    @Override
    public long getItemId(int position) {
        return statuses.get(position).getUuid().hashCode();
    }

    @Override
    public int getItemCount() {
        if(statuses == null)
            return 0;
        else
            return statuses.size();
    }

    public void clean() {
        swap(null);
        activity = null;
        fragment = null;
    }

    public PushStatus getLastItem() {
        if(statuses.size() == 0)
            return null;
        return statuses.get(statuses.size()-1);
    }
    public PushStatus getFirstItem() {
        if(statuses.size() == 0)
            return null;
        return statuses.get(0);
    }

    public int addStatusOnTop(PushStatus status) {
        statuses.add(0,status);
        return 0;
    }

    public void addStatusAtBottom(List<PushStatus> statuses) {
        this.statuses.addAll(statuses);
    }

    public int deleteStatus(String uuid) {
        Iterator<PushStatus> it = statuses.iterator();
        while(it.hasNext()) {
            PushStatus item = it.next();
            if(item.getUuid().equals(uuid)) {
                int pos = statuses.indexOf(item);
                it.remove();
                return pos;
            }
        }
        return -1;
    }
    public void swap(List<PushStatus> statuses) {
        if(this.statuses != null) {
            for (PushStatus message : this.statuses) {
                message.discard();
                message = null;
            }
            this.statuses.clear();
        }
        if(statuses != null) {
            for (PushStatus message : statuses) {
                this.statuses.add(message);
            }
        }
    }

}
