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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.ContactDeletedEvent;
import org.disrupted.rumble.database.events.ContactInsertedEvent;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.userinterface.adapter.ContactListAdapter;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentContactList  extends Fragment {

    public static final String TAG = "FragmentContactList";


    private View mView;
    private ListView contactList;
    private ContactListAdapter contactListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_group_list, container, false);
        contactList = (ListView) mView.findViewById(R.id.group_list);
        contactListAdapter = new ContactListAdapter(getActivity());
        contactList.setAdapter(contactListAdapter);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_list_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        }
        return super.onOptionsItemSelected(item);
    }

    public void getContactList() {
        DatabaseFactory.getContactDatabase(getActivity()).getContacts(onContactsLoaded);
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
                        contactListAdapter.swap(answer);
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
