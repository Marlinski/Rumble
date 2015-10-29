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
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.userinterface.fragments.FragmentChatMessageList;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;

/**
 * @author Marlinski
 */
public class HomePagerAdapter extends FragmentPagerAdapter {
    final int PAGE_COUNT = 2;
    private FragmentStatusList      statusFragment;
    private FragmentChatMessageList chatMessageFragment;

    public HomePagerAdapter(FragmentManager fm) {
        super(fm);
        statusFragment  = new FragmentStatusList();
        chatMessageFragment = new FragmentChatMessageList();
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
        return (position == 0) ? statusFragment : chatMessageFragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        Context context = RumbleApplication.getContext();
        if(position == 0)
            return context.getResources().getString(R.string.group_detail_tab_message);
        else
            return context.getResources().getString(R.string.group_detail_tab_members);
    }

}