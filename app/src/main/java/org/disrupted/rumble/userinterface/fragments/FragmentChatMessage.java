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

import android.content.Intent;
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
import org.disrupted.rumble.database.ChatMessageDatabase;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.events.ChatMessageInsertedEvent;
import org.disrupted.rumble.database.events.ChatWipedEvent;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.userinterface.activity.HomeActivity;
import org.disrupted.rumble.userinterface.activity.PopupComposeChat;
import org.disrupted.rumble.userinterface.adapter.ChatMessageListAdapter;
import org.disrupted.rumble.userinterface.events.UserComposeChatMessage;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class FragmentChatMessage extends Fragment {
    private static View mView;

    /*
    EditText    editText;
    ImageButton sendButton;
    */
    private ListView chatMessageList;
    private ChatMessageListAdapter chatMessageListAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_chat_message, container, false);

        chatMessageList = (ListView) mView.findViewById(R.id.chat_message_list);
        chatMessageListAdapter = new ChatMessageListAdapter(getActivity(), this);
        /*
        chatMessageListAdapter.registerDataSetObserver(new DataSetObserver() {
                  public void onChanged() {
                      FrameLayout progressBar = (FrameLayout) mView.findViewById(R.id.status_list_progressbar);
                      progressBar.setVisibility(View.GONE);
                  }
              }
        );
        */
        chatMessageList.setAdapter(chatMessageListAdapter);


        refreshChatMessages();
        EventBus.getDefault().register(this);

        return mView;
    }



    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        chatMessageListAdapter.clean();
        super.onDestroy();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.direct_message_list_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_discussion:
                Intent compose = new Intent(getActivity(), PopupComposeChat.class );
                startActivity(compose);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void refreshChatMessages() {
        ChatMessageDatabase.ChatMessageQueryOption options = new ChatMessageDatabase.ChatMessageQueryOption();
        options.answerLimit = 20;
        DatabaseFactory.getChatMessageDatabase(getActivity())
                    .getChatMessage(options, onChatMessagesLoaded);
    }
    DatabaseExecutor.ReadableQueryCallback onChatMessagesLoaded = new DatabaseExecutor.ReadableQueryCallback() {
        @Override
        public void onReadableQueryFinished(final Object result) {
            final ArrayList<ChatMessage> answer = (ArrayList<ChatMessage>)result;
            if (getActivity() == null)
                return;
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    chatMessageListAdapter.swap(answer);
                    chatMessageListAdapter.notifyDataSetChanged();
                    //if (getActivity() != null)
                    //    ((HomeActivity)getActivity()).refreshNotifications();
                }
            });
        }
    };

    public void onEvent(ChatMessageInsertedEvent event) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(((HomeActivity)getActivity()).isChatHasFocus())
                    refreshChatMessages();
            }
        });
    }
    public void onEvent(ChatWipedEvent event) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(((HomeActivity)getActivity()).isChatHasFocus())
                    refreshChatMessages();
            }
        });
    }
}