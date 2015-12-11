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

package org.disrupted.rumble.userinterface.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

import org.disrupted.rumble.R;

/**
 * @author Lucien Loiseau
 */
public class SimpleHistogram extends RelativeLayout {

    private final String TAG = "CombinedHistogram";

    private long size;
    private long total;

    private RelativeLayout relativeLayout;
    private View dataView;

    public SimpleHistogram(Context context) {
        super(context);
        size = 10;
        total=100;
        init();
    }

    public SimpleHistogram(Context context, AttributeSet attrs) {
        super(context, attrs);
        size = 10;
        total=100;
        init();
    }

    private void init() {
        inflate(getContext(), R.layout.view_simple_histogram, this);
        relativeLayout = (RelativeLayout)findViewById(R.id.combined_histogram_relative_layout);
        dataView = relativeLayout.findViewById(R.id.data);
    }

    public void setSize(long dataSize, long totalSize) {
        this.size  = dataSize;
        this.total = totalSize;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if(changed) {
            int widthLayout = relativeLayout.getMeasuredWidth();
            int heightLayout = relativeLayout.getMeasuredHeight();
            int dataWidth = (int) (size * widthLayout / total);
            dataWidth = (dataWidth == 0) ? 2 : dataWidth;

            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(dataWidth, heightLayout);
            dataView.setLayoutParams(lp);
        }
    }

    public void setColor(int resourceID) {
        dataView.setBackgroundColor(getResources().getColor(resourceID));
    }
}
