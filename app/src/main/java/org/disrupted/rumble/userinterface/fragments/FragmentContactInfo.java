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

package org.disrupted.rumble.userinterface.fragments;

import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.ContactDatabase;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Interface;
import org.disrupted.rumble.userinterface.adapter.ContactInfoRecyclerAdapter;
import org.disrupted.rumble.util.TimeUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Marlinski
 */
public class FragmentContactInfo extends Fragment {


    public static final String TAG = "FragmentContactInfo";

    private View   mView;
    private String contact_uid = null;
    private RecyclerView mRecyclerView;
    private ContactInfoRecyclerAdapter mRecyclerAdapter;

    public static class ContactInfoItem {
        public String title;
        public String data;
        public ContactInfoItem(String title, String data) {
            this.title = title;
            this.data = data;
        }
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();
        if(args != null) {
            this.contact_uid = args.getString("ContactID");
        }

        // inflate the view and bind the adapter
        mView = inflater.inflate(R.layout.fragment_contact_info, container, false);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.contact_info_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerAdapter = new ContactInfoRecyclerAdapter();
        mRecyclerView.setAdapter(mRecyclerAdapter);

        // get the contact from DB
        Contact contact = DatabaseFactory.getContactDatabase(RumbleApplication.getContext())
                .getContact(contact_uid);

        // create the list of information to be displayed
        Resources resources = getActivity().getResources();
        ArrayList<ContactInfoItem> infoList = new ArrayList<ContactInfoItem>();
        infoList.add(new ContactInfoItem(
                resources.getString(R.string.contact_detail_name),
                contact.getName()
        ));
        infoList.add(new ContactInfoItem(
                resources.getString(R.string.contact_detail_uid),
                contact.getUid()
        ));
        infoList.add(new ContactInfoItem(
                resources.getString(R.string.contact_detail_last_met),
                TimeUtil.timeElapsed(contact.lastMet())
        ));
        infoList.add(new ContactInfoItem(
                resources.getString(R.string.contact_detail_nb_status_rcvd),
                contact.nbStatusReceived()+""
        ));
        infoList.add(new ContactInfoItem(
                resources.getString(R.string.contact_detail_nb_status_sent),
                contact.nbStatusSent()+""
        ));
        Set<Interface> interfaces = contact.getInterfaces();
        for(Interface iface : interfaces) {
            infoList.add(new ContactInfoItem(
                    resources.getString(R.string.contact_detail_interface),
                    iface.getMacAddress()
            ));
        }
        mRecyclerAdapter.swap(infoList);

        return mView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
