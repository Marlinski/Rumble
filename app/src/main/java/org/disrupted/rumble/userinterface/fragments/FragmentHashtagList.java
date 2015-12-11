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

package org.disrupted.rumble.userinterface.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.ContactTagInterestUpdatedEvent;
import org.disrupted.rumble.database.events.HashtagInsertedEvent;
import org.disrupted.rumble.userinterface.adapter.HashtagRecyclerAdapter;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class FragmentHashtagList extends Fragment {

    public static final String TAG = "FragmentContactList";

    private View mView;
    private RecyclerView mRecyclerView;
    private HashtagRecyclerAdapter hashtagRecyclerAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        mView = inflater.inflate(R.layout.fragment_hashtag_list, container, false);
        mRecyclerView = (RecyclerView) mView.findViewById(R.id.hashtag_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        hashtagRecyclerAdapter = new HashtagRecyclerAdapter(getActivity());
        mRecyclerView.setAdapter(hashtagRecyclerAdapter);
        EventBus.getDefault().register(this);
        getHashtagList();
        return mView;
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        super.onDestroy();
    }

    public void getHashtagList() {
        DatabaseFactory.getHashtagDatabase(getActivity())
                .getHashtags(onHashtagLoaded);
    }
    private DatabaseExecutor.ReadableQueryCallback onHashtagLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            if(getActivity() == null)
                return;
            getActivity().runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<String> answer = (ArrayList<String>)(result);
                            hashtagRecyclerAdapter.swap(answer);
                        }
                    }
            );
        }
    };

    public void onEvent(HashtagInsertedEvent event) {
        getHashtagList();
    }
    public void onEvent(ContactTagInterestUpdatedEvent event) {
        if(event.contact.isLocal()) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hashtagRecyclerAdapter.notifyDataSetChanged();
                }
            });
        }
    }

}
