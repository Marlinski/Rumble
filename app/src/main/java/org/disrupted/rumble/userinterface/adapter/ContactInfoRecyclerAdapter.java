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

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.userinterface.fragments.FragmentContactInfo;

import java.util.ArrayList;

/**
 * @author Marlinski
 */
public class ContactInfoRecyclerAdapter extends RecyclerView.Adapter<ContactInfoRecyclerAdapter.ContactInfoItemHolder> {

    public static final String TAG = "ContactInfoRecyclerAdapter";

    public class ContactInfoItemHolder extends RecyclerView.ViewHolder {

        public ContactInfoItemHolder(View itemView) {
            super(itemView);
        }

        public void bindInfoItem(FragmentContactInfo.ContactInfoItem infoItem) {
            TextView titleView  = (TextView) itemView.findViewById(R.id.contact_info_title);
            TextView dataView   = (TextView) itemView.findViewById(R.id.contact_info_data);
            titleView.setText(infoItem.title);
            dataView.setText(infoItem.data);
        }
    }


    private ArrayList<FragmentContactInfo.ContactInfoItem> infoList;

    public ContactInfoRecyclerAdapter() {
        this.infoList = null;
    }

    @Override
    public ContactInfoRecyclerAdapter.ContactInfoItemHolder onCreateViewHolder(ViewGroup parent, int i) {
        LinearLayout layout = (LinearLayout) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_contact_info_list, parent, false);
        return new ContactInfoItemHolder(layout);
    }

    @Override
    public void onBindViewHolder(ContactInfoItemHolder contactInfoItemHolder, int i) {
        FragmentContactInfo.ContactInfoItem infoItem = infoList.get(i);
        contactInfoItemHolder.bindInfoItem(infoItem);
    }


    @Override
    public int getItemCount() {
        if(infoList == null)
            return 0;
        else
            return infoList.size();
    }

    public void swap(ArrayList<FragmentContactInfo.ContactInfoItem> infoList) {
        if(this.infoList != null)
            this.infoList.clear();
        this.infoList = infoList;
        notifyDataSetChanged();
    }
}
