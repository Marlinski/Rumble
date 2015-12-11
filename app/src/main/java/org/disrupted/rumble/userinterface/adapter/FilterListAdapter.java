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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.events.UserSetHashTagInterest;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class FilterListAdapter extends BaseAdapter {

    public static final String TAG = "FilterRecyclerAdapter";

    public class FilterHolder {

        TextView filterTextView;
        LinearLayout outerFilterView;
        TextView subscriptionTextView;

        public FilterHolder(View itemView) {
            filterTextView       = (TextView) itemView.findViewById(R.id.filter_text);
            outerFilterView  = (LinearLayout) itemView.findViewById(R.id.filter_outer_text);
            subscriptionTextView = (TextView) itemView.findViewById(R.id.subscription_button);
        }

        public void bindFilter(final String filter) {
            filterTextView.setText(filter);
            outerFilterView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    fragmentStatusList.onFilterClickCallback(filter);
                }
            });

            final boolean isInterested;
            Integer interest = Contact.getLocalContact().getHashtagInterests().get(filter);
            isInterested = ((interest != null) && (interest > 0));
            if(isInterested) {
                subscriptionTextView.setText(R.string.filter_subscribed);
                subscriptionTextView.setTextColor(context.getResources().getColor(R.color.white));
                subscriptionTextView.setBackgroundColor(context.getResources().getColor(R.color.green));
            } else {
                subscriptionTextView.setText(R.string.filter_not_subscribed);
                subscriptionTextView.setTextColor(context.getResources().getColor(R.color.black));
                subscriptionTextView.setBackgroundColor(context.getResources().getColor(R.color.white));
            }

            subscriptionTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if(isInterested)
                        EventBus.getDefault().post(new UserSetHashTagInterest(filter, Contact.MIN_INTEREST_TAG_VALUE));
                    else
                        EventBus.getDefault().post(new UserSetHashTagInterest(filter, Contact.MAX_INTEREST_TAG_VALUE));
                }
            });
        }
    }

    Context context;
    LayoutInflater inflater;
    List<String> filterList;
    FragmentStatusList fragmentStatusList;

    public FilterListAdapter(Context context, FragmentStatusList fragmentStatusList) {
        this.context = context;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.fragmentStatusList = fragmentStatusList;
        this.filterList = new LinkedList<String>();
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        FilterHolder holder;

        if(view == null) {
            view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.item_hashtag_filter_list, viewGroup, false);
            holder = new FilterHolder(view);
            view.setTag(holder);
        } else {
            holder = (FilterHolder) view.getTag();
        }

        String filter = filterList.get(i);
        holder.bindFilter(filter);

        return view;
    }

    @Override
    public int getCount() {
        return filterList.size();
    }

    @Override
    public long getItemId(int position) {
        return filterList.get(position).hashCode();
    }

    @Override
    public Object getItem(int i) {
        return filterList.get(i);
    }

    public void deleteFilter(String filter) {
        Iterator<String> it = filterList.iterator();
        while(it.hasNext()) {
            if(it.next().equals(filter)) {
                it.remove();
                notifyDataSetChanged();
                return;
            }
        }
    }

    public boolean addFilter(String filter) {
        for(String entry : filterList) {
            if((entry).toLowerCase().equals(filter.toLowerCase()))
                return false;
        }
        filterList.add(filter);
        notifyDataSetChanged();
        return true;
    }

    public Set<String> getFilterList() {
        Set<String> filters = new HashSet<String>();
        for(String entry : filterList) {
            filters.add(entry);
        }
        return filters;
    }

}
