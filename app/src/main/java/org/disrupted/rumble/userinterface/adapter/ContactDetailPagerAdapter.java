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
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.userinterface.fragments.FragmentContactInfo;
import org.disrupted.rumble.userinterface.fragments.FragmentStatusList;

/**
 * @author Lucien Loiseau
 */
public class ContactDetailPagerAdapter extends FragmentPagerAdapter {

    final int PAGE_COUNT = 2;
    private FragmentStatusList  statusFragment;
    private FragmentContactInfo infoFragment;

    public ContactDetailPagerAdapter(FragmentManager fm, Bundle args) {
        super(fm);
        infoFragment = new FragmentContactInfo();
        infoFragment.setArguments(args);

        statusFragment  = new FragmentStatusList();
        args.putBoolean("noCoordinatorLayout",true);
        statusFragment.setArguments(args);
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
        return (position == 0) ? statusFragment : infoFragment;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        Context context = RumbleApplication.getContext();
        if(position == 0)
            return context.getResources().getString(R.string.contact_detail_tab_message);
        else
            return context.getResources().getString(R.string.contact_detail_tab_info);
    }

}
