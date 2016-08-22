/*
 * Copyright (C) 2014 Lucien Loiseau
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.disrupted.rumble.R;

import java.util.List;

/**
 * @author Lucien Loiseau
 */
public class IconTextListAdapter extends BaseAdapter {

    private static final String TAG = "NeighborListAdapter";

    private final LayoutInflater inflater;
    private List<IconTextItem> itemlist;

    public IconTextListAdapter(Activity activity, List<IconTextItem> itemlist) {
        this.inflater = LayoutInflater.from(activity);
        this.itemlist = itemlist;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View item = inflater.inflate(R.layout.item_icontext, viewGroup, false);
        ImageView icon = (ImageView) item.findViewById(R.id.item_icon);
        TextView text = (TextView) item.findViewById(R.id.item_text);
        icon.setImageResource(itemlist.get(i).getIcon());
        text.setText(itemlist.get(i).getText());
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
}

