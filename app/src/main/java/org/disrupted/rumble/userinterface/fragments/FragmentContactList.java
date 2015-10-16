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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.ContactDatabase;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.ContactDeletedEvent;
import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.adapter.ContactRecyclerAdapter;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentContactList  extends Fragment {

    public static final String TAG = "FragmentContactList";


    private View mView;
    private RecyclerView mRecyclerView;
    private ContactRecyclerAdapter contactRecyclerAdapter;
    private String   filter_gid = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        Bundle args = getArguments();
        if(args != null) {
            this.filter_gid = args.getString("GroupID");
        }

        mView = inflater.inflate(R.layout.fragment_contact_list, container, false);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.contact_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        contactRecyclerAdapter = new ContactRecyclerAdapter(getActivity());
        mRecyclerView.setAdapter(contactRecyclerAdapter);
        EventBus.getDefault().register(this);
        getContactList();
        return mView;
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void getContactList() {
        ContactDatabase.ContactQueryOption options = new ContactDatabase.ContactQueryOption();
        options.answerLimit = 20;
        options.order_by = ContactDatabase.ContactQueryOption.ORDER_BY.LAST_TIME_MET;

        if(filter_gid != null) {
            options.filterFlags |= ContactDatabase.ContactQueryOption.FILTER_GROUP;
            options.gid = filter_gid;
        } else {
            options.filterFlags |= ContactDatabase.ContactQueryOption.FILTER_LOCAL;
            options.local = false;
        }
        DatabaseFactory.getContactDatabase(getActivity())
                .getContacts(options, onContactsLoaded);
    }
    private DatabaseExecutor.ReadableQueryCallback onContactsLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            if(getActivity() == null)
                return;
            getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<Contact> answer = (ArrayList<Contact>)(result);
                        contactRecyclerAdapter.swap(answer);
                    }
                }
            );
        }
    };

    public void onEvent(ContactInsertedEvent event) {
        getContactList();
    }
    public void onEvent(ContactDeletedEvent event) {
        getContactList();
    }

}
