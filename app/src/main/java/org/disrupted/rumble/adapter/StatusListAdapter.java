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
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.NewStatusEvent;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class StatusListAdapter extends BaseAdapter implements DatabaseExecutor.ReadableQueryCallback{

    private static final String TAG = "NeighborListAdapter";

    private Activity       activity;
    private LayoutInflater inflater;
    private Cursor         statuses;

    public StatusListAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        statuses = null;
        EventBus.getDefault().register(this);
    }

    public void clean() {
        if(statuses != null)
            statuses.close();
        statuses = null;
        inflater = null;
        activity = null;
        EventBus.getDefault().unregister(this);
    }

    public void getStatuses() {
        DatabaseFactory.getStatusDatabase(activity).getStatuses(this);
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View status = inflater.inflate(R.layout.status_item, null, true);
        ImageView icon    = (ImageView) status.findViewById(R.id.status_item_icon);
        TextView  author  = (TextView) status.findViewById(R.id.status_item_author);
        TextView  post    = (TextView) status.findViewById(R.id.status_item_body);
        TextView  created = (TextView) status.findViewById(R.id.status_item_created);
        TextView  arrived = (TextView) status.findViewById(R.id.status_item_received);

        if(!statuses.moveToPosition(i))
            return status;
        author.setText(statuses.getString(1));
        post.setText(statuses.getString(2));
        created.setText(new TimeElapsed(statuses.getLong(4)).display());
        created.setText(new TimeElapsed(statuses.getLong(4)).display());
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

    @Override
    public void onReadableQueryFinished(Cursor answer) {
        if(statuses != null)
            statuses.close();
        statuses = answer;
        activity.runOnUiThread(new Runnable() {
                                   @Override
                                   public void run() {
                                       notifyDataSetChanged();
                                   }
                               });
    }

    public void onEvent(NewStatusEvent status) {
        getStatuses();
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
