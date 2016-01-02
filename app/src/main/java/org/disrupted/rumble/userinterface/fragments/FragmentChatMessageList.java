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

import android.os.Vibrator;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import org.disrupted.rumble.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.ChatMessageDatabase;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.network.protocols.events.ChatMessageReceived;
import org.disrupted.rumble.database.events.ChatMessageInsertedEvent;
import org.disrupted.rumble.database.events.ChatMessageUpdatedEvent;
import org.disrupted.rumble.database.events.ChatWipedEvent;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.userinterface.activity.HomeActivity;
import org.disrupted.rumble.userinterface.adapter.ChatMessageRecyclerAdapter;
import org.disrupted.rumble.userinterface.events.UserComposeChatMessage;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class FragmentChatMessageList extends Fragment {

    private static final String TAG = "FragmentChatMessage";

    private static View mView;

    private RecyclerView mRecyclerView;
    private ChatMessageRecyclerAdapter messageRecyclerAdapter;

    private FrameLayout sendBox;
    private EditText    compose;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.fragment_chatmessage_list, container, false);

        mRecyclerView = (RecyclerView) mView.findViewById(R.id.chat_message_list);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        linearLayoutManager.setReverseLayout(true);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        messageRecyclerAdapter = new ChatMessageRecyclerAdapter(getActivity(), this);
        mRecyclerView.setAdapter(messageRecyclerAdapter);

        compose = (EditText) mView.findViewById(R.id.chat_compose);

        sendBox = (FrameLayout) mView.findViewById(R.id.chat_send_box);
        sendBox.setOnClickListener(onClickSend);

        refreshChatMessages();
        EventBus.getDefault().register(this);

        return mView;
    }

    @Override
    public void onDestroy() {
        if(EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().unregister(this);
        messageRecyclerAdapter.clean();
        super.onDestroy();
    }

    public View.OnClickListener onClickSend = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            try {
                String message = compose.getText().toString();
                if (message.equals(""))
                    return;

                Contact localContact = Contact.getLocalContact();
                long now = System.currentTimeMillis();
                ChatMessage chatMessage = new ChatMessage(localContact, message, now, now, RumbleProtocol.protocolID);
                chatMessage.setUserRead(true);

                EventBus.getDefault().post(new UserComposeChatMessage(chatMessage));
            } catch (Exception e) {
                Log.e(TAG, "[!] " + e.getMessage());
            } finally {
                compose.setText("");
            }
        }
    };

    public void pageIn() {
        refreshChatMessages();
    }
    public void pageOut() {
        InputMethodManager imm = (InputMethodManager) RumbleApplication.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(compose.getWindowToken(), 0);
    }

    private void refreshChatMessages() {
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
                    messageRecyclerAdapter.swap(answer);
                    messageRecyclerAdapter.notifyDataSetChanged();
                    mRecyclerView.smoothScrollToPosition(0);
                }
            });
        }
    };

    public void onEvent(final ChatMessageUpdatedEvent event) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ChatMessage message = event.chatMessage;
                int pos = messageRecyclerAdapter.updateChatMessage(message);
                if(pos >= 0)
                    messageRecyclerAdapter.notifyItemChanged(pos);
            }
        });
    }
    public void onEvent(final ChatMessageInsertedEvent event) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final ChatMessage message = event.chatMessage;
                if(((HomeActivity)getActivity()).isChatHasFocus()) {
                    int pos = messageRecyclerAdapter.addChatMessage(message);
                    messageRecyclerAdapter.notifyItemInserted(pos);
                    mRecyclerView.smoothScrollToPosition(pos);
                }
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

    /** Vibrates when a chat message is recieved **/
    public void onEvent(ChatMessageReceived event) {
        getActivity().runOnUiThread(new Runnable () {
	    @Override
	    public void run() {
	        if(!((HomeActivity)getActivity()).isChatHasFocus()) {
		    Vibrator vibrator = (Vibrator) getActivity().getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
		    vibrator.vibrate(300);
		}
	    }
	});
    }
}
