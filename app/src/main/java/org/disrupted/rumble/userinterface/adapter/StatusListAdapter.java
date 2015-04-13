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

package org.disrupted.rumble.userinterface.adapter;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.support.v7.widget.PopupMenu;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.disrupted.rumble.R;
import org.disrupted.rumble.message.StatusMessage;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserReadStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatusListAdapter extends BaseAdapter{

    private static final String TAG = "NeighborListAdapter";

    private FragmentStatusList fragment;
    private Activity           activity;
    private LayoutInflater     inflater;
    private ArrayList<StatusItemViewHolder> viewHolders;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();

    public class StatusItemViewHolder {
        boolean       refresh;
        StatusMessage message;
        View statusView;
        ImageView avatarView;
        TextView  authorView;
        TextView  textView;
        TextView  tocView;
        TextView  toaView;
        ImageView attachedView;
        Bitmap    imageBitmap;
        ImageView moreView;
        LinearLayout box;
        public StatusItemViewHolder(StatusMessage message) {
            this.message = message;
            refresh = true;
            statusView = null;
            avatarView = null;
            authorView = null;
            textView = null;
            tocView = null;
            toaView = null;
            attachedView = null;
            moreView = null;
            imageBitmap = null;
            box = null;
        }
    }

    public StatusListAdapter(Activity activity, FragmentStatusList fragment) {
        this.activity = activity;
        this.fragment = fragment;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.viewHolders = new ArrayList<StatusItemViewHolder>();
    }

    public void clean() {
        swap(null);
        inflater = null;
        activity = null;
        fragment = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        StatusItemViewHolder viewHolder = viewHolders.get(i);
        if(viewHolder.statusView == null) {
            viewHolder.statusView = inflater.inflate(R.layout.status_item, null, true);
            viewHolder.avatarView = (ImageView) viewHolder.statusView.findViewById(R.id.status_item_avatar);
            viewHolder.authorView = (TextView) viewHolder.statusView.findViewById(R.id.status_item_author);
            viewHolder.textView = (TextView) viewHolder.statusView.findViewById(R.id.status_item_body);
            viewHolder.tocView = (TextView) viewHolder.statusView.findViewById(R.id.status_item_created);
            viewHolder.toaView = (TextView) viewHolder.statusView.findViewById(R.id.status_item_received);
            viewHolder.attachedView = (ImageView) viewHolder.statusView.findViewById(R.id.status_item_attached_image);
            viewHolder.moreView = (ImageView) viewHolder.statusView.findViewById(R.id.status_item_more_options);
            viewHolder.box = (LinearLayout) viewHolder.statusView.findViewById(R.id.status_item_box);
            viewHolder.refresh = true;
        }

        if(viewHolder.refresh) {

            // we draw the avatar
            ColorGenerator generator = ColorGenerator.DEFAULT;
            viewHolder.avatarView.setImageDrawable(
                    builder.build(viewHolder.message.getAuthor().substring(0, 1),
                            generator.getColor(viewHolder.message.getAuthor())));

            // we draw the author field
            viewHolder.authorView.setText(viewHolder.message.getAuthor());
            viewHolder.tocView.setText(new TimeElapsed(viewHolder.message.getTimeOfCreation()).display());

            // we draw the status (with clickable hashtag)
            if (viewHolder.message.getPost().length() == 0) {
                viewHolder.statusView.setVisibility(View.GONE);
            } else {
                SpannableString ss = new SpannableString(viewHolder.message.getPost());
                int beginCharPosition = -1;
                int j;
                for (j = 0; j < viewHolder.message.getPost().length(); j++) {
                    if (viewHolder.message.getPost().charAt(j) == '#')
                        beginCharPosition = j;
                    if ((viewHolder.message.getPost().charAt(j) == ' ') && (beginCharPosition >= 0)) {
                        final String word = viewHolder.message.getPost().substring(beginCharPosition, j);
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
                    final String word = viewHolder.message.getPost().substring(beginCharPosition, j);
                    ClickableSpan clickableSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View textView) {
                            fragment.addFilter(word);
                        }
                    };
                    ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                viewHolder.textView.setText(ss);
                viewHolder.textView.setMovementMethod(LinkMovementMethod.getInstance());

                // we draw the attached file (if any)
                if (!viewHolder.message.getFileName().equals("")) {
                    try {
                        File attachedFile = new File(FileUtil.getReadableAlbumStorageDir(), viewHolder.message.getFileName());
                        if (!attachedFile.isFile() || !attachedFile.exists())
                            throw new IOException("file does not exists");

                        viewHolder.imageBitmap = ThumbnailUtils.extractThumbnail(
                                BitmapFactory.decodeFile(attachedFile.getAbsolutePath()),
                                96,
                                96);
                        viewHolder.attachedView.setImageBitmap(viewHolder.imageBitmap);
                        viewHolder.attachedView.setVisibility(View.VISIBLE);
                        final Uri uri = Uri.parse("file:" + attachedFile.getAbsolutePath());
                        viewHolder.attachedView.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Intent intent = new Intent(android.content.Intent.ACTION_VIEW);
                                intent.setDataAndType(uri, "image/*");
                                activity.startActivity(intent);
                            }
                        });
                    } catch (IOException ignore) {
                    }
                }
            }

            // we enable the click for more options
            final String uuid = viewHolder.message.getUuid();
            viewHolder.moreView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    PopupMenu popupMenu = new PopupMenu(activity, view);
                    popupMenu.getMenu().add(Menu.NONE, 1, Menu.NONE, R.string.status_more_option_like);
                    popupMenu.getMenu().add(Menu.NONE, 2, Menu.NONE, R.string.status_more_option_save);
                    popupMenu.getMenu().add(Menu.NONE, 3, Menu.NONE, R.string.status_more_option_delete);
                    popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem menuItem) {
                            switch (menuItem.getItemId()) {
                                case 1:
                                    EventBus.getDefault().post(new UserLikedStatus(uuid));
                                    break;
                                case 2:
                                    EventBus.getDefault().post(new UserSavedStatus(uuid));
                                    break;
                                case 3:
                                    EventBus.getDefault().post(new UserDeleteStatus(uuid));
                                    break;
                                default:
                                    return false;
                            }
                            return false;
                        }
                    });
                    popupMenu.show();
                }
            });

            if (!viewHolder.message.hasUserReadAlready() || (((System.currentTimeMillis() / 1000L) - viewHolder.message.getTimeOfArrival()) < 60)) {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                    viewHolder.box.setBackgroundDrawable(activity.getResources().getDrawable(R.drawable.status_shape_unread));
                } else {
                    viewHolder.box.setBackground(activity.getResources().getDrawable(R.drawable.status_shape_unread));
                }
                if (!viewHolder.message.hasUserReadAlready())
                    EventBus.getDefault().post(new UserReadStatus(uuid));
            }
            viewHolder.refresh = false;
        }

        return viewHolder.statusView;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        if(viewHolders == null)
            return 0;
        return i;
    }

    @Override
    public int getCount() {
        if(viewHolders == null)
            return 0;
        return viewHolders.size();
    }

    public void swap(ArrayList<StatusMessage> statuses) {
        if(this.viewHolders != null) {
            for (StatusItemViewHolder view : this.viewHolders) {
                view.message.discard();
                if(view.imageBitmap != null) {
                    view.imageBitmap.recycle();
                    view.imageBitmap = null;
                }
                view.message = null;
            }
            this.viewHolders.clear();
        }
        if(statuses != null) {
            for (StatusMessage message : statuses) {
                StatusItemViewHolder view = new StatusItemViewHolder(message);
                viewHolders.add(view);
            }
        }
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
