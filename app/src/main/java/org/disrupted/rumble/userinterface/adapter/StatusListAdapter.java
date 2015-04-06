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
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.support.v7.widget.PopupMenu;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.userinterface.events.UserDeleteStatus;
import org.disrupted.rumble.userinterface.events.UserLikedStatus;
import org.disrupted.rumble.userinterface.events.UserSavedStatus;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;
import org.disrupted.rumble.util.FileUtil;

import java.io.File;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatusListAdapter extends BaseAdapter{

    private static final String TAG = "NeighborListAdapter";

    private FragmentStatusList fragment;
    private Activity           activity;
    private LayoutInflater     inflater;
    private Cursor             statuses;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();

    public StatusListAdapter(Activity activity, FragmentStatusList fragment) {
        this.activity = activity;
        this.fragment = fragment;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        statuses = null;
    }

    public void clean() {
        if(statuses != null)
            statuses.close();
        statuses = null;
        inflater = null;
        activity = null;
        fragment = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View status = inflater.inflate(R.layout.status_item, null, true);
        ImageView avatarView   = (ImageView) status.findViewById(R.id.status_item_avatar);
        TextView  authorView   = (TextView) status.findViewById(R.id.status_item_author);
        TextView  statusView     = (TextView) status.findViewById(R.id.status_item_body);
        TextView  tocView      = (TextView) status.findViewById(R.id.status_item_created);
        TextView  toaView      = (TextView) status.findViewById(R.id.status_item_received);
        ImageView attachedView = (ImageView) status.findViewById(R.id.status_item_attached_image);
        ImageView moreView     = (ImageView) status.findViewById(R.id.status_item_more_options);

        if(!statuses.moveToPosition(i))
            return status;

        final String author   = statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.AUTHOR));
        final String filename = statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.FILE_NAME));
        final String post     = statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.POST));
        final long toc        = statuses.getLong(statuses.getColumnIndexOrThrow(StatusDatabase.TIME_OF_CREATION));
        final String uuid     = statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.UUID));

        // we set the avatar
        ColorGenerator generator = ColorGenerator.DEFAULT;
        avatarView.setImageDrawable(builder.build(author.substring(0,1), generator.getColor(author)));

        // we set the author field
        authorView.setText(author);
        tocView.setText(new TimeElapsed(toc).display());

        // we draw the attached file (if any)
        if(!filename.equals("")) {
            File directory = FileUtil.getReadableAlbumStorageDir();
            if (directory != null) {
                File attachedFile = new File(directory + File.separator + filename);
                Bitmap bitmapImage = BitmapFactory.decodeFile(attachedFile.getAbsolutePath());
                attachedView.setImageBitmap(bitmapImage);
                attachedView.setVisibility(View.VISIBLE);
            }
        }

        // we set the status
        if(post.length() == 0) {
            statusView.setVisibility(View.GONE);
        } else {
            SpannableString ss = new SpannableString(post);
            int beginCharPosition = -1;
            int j;
            for (j = 0; j < post.length(); j++) {
                if (post.charAt(j) == '#')
                    beginCharPosition = j;
                if ((post.charAt(j) == ' ') && (beginCharPosition >= 0)) {
                    final String word = post.substring(beginCharPosition, j);
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
                final String word = post.substring(beginCharPosition, j);
                ClickableSpan clickableSpan = new ClickableSpan() {
                    @Override
                    public void onClick(View textView) {
                        fragment.addFilter(word);
                    }
                };
                ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            statusView.setText(ss);
            statusView.setMovementMethod(LinkMovementMethod.getInstance());
        }

        // we enable the click for more options
        moreView.setOnClickListener(new View.OnClickListener() {
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
                            default: return false;
                        }
                        return false;
                    }
                });
                popupMenu.show();
            }
        });

        return status;
    }

    @Override
    public Object getItem(int i) {
        return null;
    }

    @Override
    public long getItemId(int i) {
        if(statuses == null)
            return 0;
        return i;
    }

    @Override
    public int getCount() {
        if(statuses == null)
            return 0;
        return statuses.getCount();
    }

    public void swap(Cursor cursor) {
        if(statuses != null)
            statuses.close();
        statuses = cursor;
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
