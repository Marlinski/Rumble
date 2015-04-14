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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.message.Group;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class GroupListAdapter extends BaseAdapter {


    private Context context;
    private LayoutInflater inflater;
    private ArrayList<Group> groupList;


    public GroupListAdapter(Context context) {
        this.context = context;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.groupList = null;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View layout = inflater.inflate(R.layout.group_list_item, null, true);
        TextView group_name   = (TextView) layout.findViewById(R.id.group_name);
        group_name.setText(groupList.get(i).getName());
        return layout;
    }

    @Override
    public long getItemId(int i) {
        if(groupList == null)
            return 0;
        if(groupList.isEmpty())
            return 0;
        return i;
    }

    @Override
    public Object getItem(int i) {
        return groupList.get(i);
    }

    @Override
    public int getCount() {
        if(groupList == null)
            return 0;
        else
            return groupList.size();
    }

    public void swap(ArrayList<Group> groupList) {
        if(this.groupList == null)
            this.groupList = groupList;
        else {
            this.groupList.clear();
            this.groupList = groupList;
        }
        notifyDataSetChanged();
    }

}
