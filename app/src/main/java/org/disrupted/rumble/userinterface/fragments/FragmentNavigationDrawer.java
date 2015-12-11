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

package org.disrupted.rumble.userinterface.fragments;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.activity.ContactListActivity;
import org.disrupted.rumble.userinterface.activity.GroupListActivity;
import org.disrupted.rumble.userinterface.activity.HashtagListActivity;
import org.disrupted.rumble.userinterface.activity.HomeActivity;
import org.disrupted.rumble.userinterface.activity.SettingsActivity;
import org.disrupted.rumble.userinterface.adapter.IconTextItem;
import org.disrupted.rumble.userinterface.adapter.IconTextListAdapter;
import org.disrupted.rumble.network.NetworkCoordinator;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Lucien Loiseau
 */
public class FragmentNavigationDrawer extends Fragment implements ListView.OnItemClickListener{

    public static final String TAG = "DrawerNavigationFragment";

    private LinearLayout mDrawerFragmentLayout;
    private ListView mFirstListView;
    private IconTextListAdapter mFirstListAdapter;
    List<IconTextItem> firstList;
    Contact localContact;
    private static final TextDrawable.IBuilder builder = TextDrawable.builder().rect();

    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mDrawerFragmentLayout = (LinearLayout) inflater.inflate(R.layout.fragment_navigation_drawer, container, false);

        /* set the header */
        localContact = Contact.getLocalContact();
        RelativeLayout header = (RelativeLayout) mDrawerFragmentLayout.findViewById(R.id.header_layout);
        TextView  user_name   = (TextView)  mDrawerFragmentLayout.findViewById(R.id.header_user_name);
        TextView  user_id     = (TextView)  mDrawerFragmentLayout.findViewById(R.id.header_user_id);

        user_name.setText(localContact.getName());
        user_id.setText(localContact.getUid());
        ColorGenerator generator = ColorGenerator.DEFAULT;
        header.setBackgroundDrawable(
                builder.build(localContact.getName().substring(0, 1),
                        generator.getColor(localContact.getUid())));

        /* set the navigation menu */
        Resources res = getActivity().getResources();
        mFirstListView   = (ListView) mDrawerFragmentLayout.findViewById(R.id.navigation_first_item_list);
        firstList = new LinkedList<IconTextItem>();
        firstList.add(new IconTextItem(R.drawable.ic_group_white_24dp, res.getString(R.string.navigation_drawer_group), 1));
        firstList.add(new IconTextItem(R.drawable.ic_person_white_24dp, res.getString(R.string.navigation_drawer_contacts), 2));
        firstList.add(new IconTextItem(R.drawable.ic_hashtag, res.getString(R.string.navigation_drawer_hashtag), 3));
        firstList.add(new IconTextItem(R.drawable.ic_settings_applications_white_24dp, res.getString(R.string.navigation_drawer_settings), 4));
        firstList.add(new IconTextItem(R.drawable.ic_close_white_24dp, res.getString(R.string.navigation_drawer_exit), 5));

        mFirstListAdapter = new IconTextListAdapter(getActivity(), firstList);
        mFirstListView.setAdapter(mFirstListAdapter);
        mFirstListView.setOnItemClickListener(this);

        return mDrawerFragmentLayout;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        ((HomeActivity)getActivity()).slidingMenu.toggle();
        switch(firstList.get(position).getID()) {
            case 1:
                Intent groupActivity = new Intent(getActivity(), GroupListActivity.class );
                startActivity(groupActivity);
                getActivity().overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                break;
            case 2:
                Intent contactActivity = new Intent(getActivity(), ContactListActivity.class );
                startActivity(contactActivity);
                getActivity().overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                break;
            case 3:
                Intent hashtagActivity = new Intent(getActivity(), HashtagListActivity.class );
                startActivity(hashtagActivity);
                getActivity().overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                break;
            case 4:
                Intent settings = new Intent(getActivity(), SettingsActivity.class );
                startActivity(settings);
                getActivity().overridePendingTransition(R.anim.activity_open_enter, R.anim.activity_open_exit);
                break;
            case 5:
                Intent stopIntent = new Intent(getActivity(), NetworkCoordinator.class);
                getActivity().stopService(stopIntent);
                getActivity().finish();
                break;
            default:
        }
    }

}
