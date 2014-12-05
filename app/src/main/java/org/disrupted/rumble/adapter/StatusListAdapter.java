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

package org.disrupted.rumble.adapter;

import android.app.Activity;
import android.app.Fragment;
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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.disrupted.rumble.HomeActivity;
import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.StatusDatabase;
import org.disrupted.rumble.database.events.NewStatusEvent;
import org.disrupted.rumble.fragments.FragmentStatusList;
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
        ImageView iconView    = (ImageView) status.findViewById(R.id.status_item_icon);
        TextView  authorView  = (TextView) status.findViewById(R.id.status_item_author);
        TextView  postView    = (TextView) status.findViewById(R.id.status_item_body);
        TextView  tocView = (TextView) status.findViewById(R.id.status_item_created);
        TextView  toaView = (TextView) status.findViewById(R.id.status_item_received);
        ImageView attachedView = (ImageView) status.findViewById(R.id.status_item_attached_image);

        if(!statuses.moveToPosition(i))
            return status;
        authorView.setText(statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.AUTHOR)));
        tocView.setText(new TimeElapsed(statuses.getLong(statuses.getColumnIndexOrThrow(StatusDatabase.TIME_OF_CREATION))).display());

        String filename = statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.FILE_NAME));

        if(!filename.equals("")) {
            File directory = FileUtil.getReadableAlbumStorageDir();
            if (directory != null) {
                File attachedFile = new File(directory + File.separator + filename);
                Bitmap bitmapImage = BitmapFactory.decodeFile(attachedFile.getAbsolutePath());
                attachedView.setImageBitmap(bitmapImage);
                attachedView.setVisibility(View.VISIBLE);
            }
        }

        String post = statuses.getString(statuses.getColumnIndexOrThrow(StatusDatabase.POST));
        SpannableString ss = new SpannableString(post);
        int beginCharPosition = -1;
        int j;
        for(j=0; j < post.length(); j++)
        {
            if(post.charAt(j) == '#')
                beginCharPosition = j;
            if((post.charAt(j) == ' ') && (beginCharPosition >= 0)){
                final String word = post.substring(beginCharPosition,j);
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
        if(beginCharPosition >= 0) {
            final String word = post.substring(beginCharPosition,j);
            ClickableSpan clickableSpan = new ClickableSpan() {
                @Override
                public void onClick(View textView) {
                    fragment.addFilter(word);
                }
            };
            ss.setSpan(clickableSpan, beginCharPosition, j, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        postView.setText(ss);
        postView.setMovementMethod(LinkMovementMethod.getInstance());

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

        private static final long ONE_SECOND_IN_MILLIS = 1000;
        private static final long ONE_MINUTE_IN_MILLIS = 60 * ONE_SECOND_IN_MILLIS;
        private static final long ONE_HOUR_IN_MILLIS = 60 * ONE_MINUTE_IN_MILLIS;
        private static final long ONE_DAY_IN_MILLIS = 24 * ONE_HOUR_IN_MILLIS;
        private static final long ONE_MONTH_IN_MILLIS = 30 * ONE_DAY_IN_MILLIS;
        private static final long ONE_YEAR_IN_MILLIS = 365 * ONE_DAY_IN_MILLIS;

        private long time;

        TimeElapsed(long timeInMillisSinceEpoch) {
            this. time = timeInMillisSinceEpoch;
        }

        public String display() {
            Resources res = activity.getResources();
            if(time < ONE_MINUTE_IN_MILLIS)
                return getTimeInSeconds()+" "+res.getString(R.string.seconds_ago);

            if(time < 2*ONE_MINUTE_IN_MILLIS)
                return res.getString(R.string.minute_ago);
            if(time < ONE_HOUR_IN_MILLIS)
                return getTimeInMinutes()+" "+res.getString(R.string.minutes_ago);

            if(time < 2*ONE_HOUR_IN_MILLIS)
                return res.getString(R.string.hour_ago);
            if(time < ONE_DAY_IN_MILLIS)
                return getTimeInHours()+" "+res.getString(R.string.hours_ago);

            if(time < 2*ONE_DAY_IN_MILLIS)
                return res.getString(R.string.day_ago);
            if(time < ONE_MONTH_IN_MILLIS)
                return getTimeInDays()+" "+res.getString(R.string.days_ago);

            if(time < 2*ONE_MONTH_IN_MILLIS)
                return res.getString(R.string.month_ago);
            if(time < ONE_YEAR_IN_MILLIS)
                return getTimeInDays()+" "+res.getString(R.string.months_ago);

            if(time < 2*ONE_YEAR_IN_MILLIS)
                return res.getString(R.string.year_ago);
            if(time < 10*ONE_YEAR_IN_MILLIS)
                return getTimeInDays()+" "+res.getString(R.string.years_ago);

            return res.getString(R.string.too_old);
        }

        private String getTimeInSeconds(){
            return Long.toString(time/ONE_SECOND_IN_MILLIS);
        }
        private String getTimeInMinutes(){
            return Long.toString(time/ONE_MINUTE_IN_MILLIS);
        }
        private String getTimeInHours(){
            return Long.toString(time/ONE_HOUR_IN_MILLIS);
        }
        private String getTimeInDays(){
            return Long.toString(time/ONE_DAY_IN_MILLIS);
        }
        private String getTimeInMonths(){
            return Long.toString(time/ONE_MONTH_IN_MILLIS);
        }
        private String getTimeInYears(){
            return Long.toString(time/ONE_YEAR_IN_MILLIS);
        }

    }

}
