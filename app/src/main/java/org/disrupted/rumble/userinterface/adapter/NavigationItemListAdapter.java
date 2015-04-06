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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.disrupted.rumble.R;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Marlinski
 */
public class NavigationItemListAdapter extends BaseAdapter {

    private static final String TAG = "NeighborListAdapter";

    private final Activity activity;
    private final LayoutInflater inflater;
    private List<NavigationItem> itemlist;
    private int checked;

    public NavigationItemListAdapter(Activity activity) {
        this.activity = activity;
        this.inflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.itemlist = new LinkedList<NavigationItem>();
    }

    public NavigationItemListAdapter(Activity activity, List<NavigationItem> itemlist) {
        this.activity = activity;
        this.inflater = LayoutInflater.from(activity);
        this.itemlist = itemlist;
        this.checked  = 0;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View item = inflater.inflate(R.layout.navigation_item, null, true);
        ImageView icon = (ImageView) item.findViewById(R.id.navigation_item_icon);
        TextView text = (TextView) item.findViewById(R.id.navigation_item_text);
        View checkView = (View) item.findViewById(R.id.navigation_item_check);
        icon.setImageResource(itemlist.get(i).getIcon());
        text.setText(itemlist.get(i).getText());
        if(i == checked) {
            checkView.setVisibility(View.VISIBLE);
        }
        return item;
    }

    @Override
    public Object getItem(int i) {
        return itemlist.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getCount() {
        return itemlist.size();
    }

    public boolean setChecked(int position) {
        boolean isDifferent = (position != this.checked);
        this.checked = position;
        notifyDataSetChanged();
        return isDifferent;
    }
}

