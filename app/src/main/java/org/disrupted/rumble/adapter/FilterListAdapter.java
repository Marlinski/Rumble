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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.disrupted.rumble.R;

import java.util.LinkedList;
import java.util.List;
import java.util.zip.Inflater;

/**
 * @author Marlinski
 */
public class FilterListAdapter extends BaseAdapter {

    Context context;
    LinearLayout filterListLayout;
    LayoutInflater inflater;
    List<String> filterList;

    public FilterListAdapter(Context context) {
        this.context = context;
        this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.filterList = new LinkedList<String>();
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View filterView = inflater.inflate(R.layout.filter_item, null, true);
        TextView filter = (TextView) filterView.findViewById(R.id.filter_text);
        String filterText = filterList.get(i);
        if(filterText.length() > 9)
            filterText = filterText.substring(0,9) + "..";
        filter.setText(filterText);
        return filterView;
    }

    @Override
    public long getItemId(int i) {
        if(filterList == null)
            return 0;
        if(filterList.isEmpty())
            return 0;
        return i;
    }

    @Override
    public Object getItem(int i) {
        return filterList.get(i);
    }

    @Override
    public int getCount() {
        if(filterList != null)
            return filterList.size();
        return 0;
    }

    public void swap(List<String> filterList) {
        if(this.filterList != null)
            this.filterList.clear();
        this.filterList = filterList;
    }

    public void deleteFilter(String filter) {
        filterList.remove(filter);
    }

    public void addFilter(String filter) {
        if(filterList.contains(filter.toLowerCase()))
            return;
        filterList.add(filter.toLowerCase());
    }

    public List<String> getFilterList() {
        return filterList;
    }

}
