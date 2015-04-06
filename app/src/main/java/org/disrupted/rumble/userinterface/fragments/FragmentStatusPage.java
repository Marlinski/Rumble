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

package org.disrupted.rumble.userinterface.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TabHost;

import org.disrupted.rumble.R;
import org.disrupted.rumble.TabFactory;
import org.disrupted.rumble.userinterface.adapter.PagerAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Marlinski
 */
public class FragmentStatusPage extends Fragment implements TabHost.OnTabChangeListener, ViewPager.OnPageChangeListener {

    private PagerAdapter pagerAdapter;
    private ViewPager mViewPager;
    private TabHost mTabHost;
    private View mView;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.viewpager_layout, container, false);
        mViewPager = (ViewPager) mView.findViewById(R.id.viewpager);
        initializeTab();
        return mView;
    }

    private void initializeTab() {
        mTabHost = (TabHost) mView.findViewById(android.R.id.tabhost);
        mTabHost.setup();

        AddTab(getActivity(), this.mTabHost, this.mTabHost.newTabSpec("Status").setIndicator("Status"));
        AddTab(getActivity(), this.mTabHost, this.mTabHost.newTabSpec("Hashtags").setIndicator("Hashtags"));
        //AddTab(this, this.mTabHost, this.mTabHost.newTabSpec("Discussions").setIndicator("Discussions"));

        List<Fragment> fragments = new ArrayList<Fragment>();
        fragments.add(new FragmentStatusList());
        fragments.add(new FragmentHashTagsList());
        //fragments.add(new FragmentDiscussionList());

        pagerAdapter = new PagerAdapter(getActivity().getSupportFragmentManager(), fragments);
        mViewPager.setAdapter(pagerAdapter);
        mViewPager.setOnPageChangeListener(this);
        mTabHost.setOnTabChangedListener(this);
    }

    private static void AddTab(Context context, TabHost tabHost, TabHost.TabSpec tabSpec) {
        tabSpec.setContent(new TabFactory(context));
        tabHost.addTab(tabSpec);
    }

    public void onTabChanged(String tag) {
        int pos = this.mTabHost.getCurrentTab();
        this.mViewPager.setCurrentItem(pos);
    }

    @Override
    public void onPageSelected(int arg0) {
    }
    @Override
    public void onPageScrollStateChanged(int i) {
    }
    @Override
    public void onPageScrolled(int i, float v, int i2) {
        int pos = this.mViewPager.getCurrentItem();
        this.mTabHost.setCurrentTab(pos);
    }

}
