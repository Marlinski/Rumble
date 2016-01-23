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

package org.disrupted.rumble.userinterface.activity;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.ChatMessage;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.util.HashUtil;

/**
 * @author Lucien Loiseau
 */
public class LoginScreen extends Activity{

    private static final String TAG = "LoginScreen";
    private static String RUMBLE_AUTHOR_NAME = "Marlinski (http://disruptedsystems.org)";

    private Button loginButton;
    private EditText username;
    private LinearLayout loginScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_login_screen);

        loginScreen  = (LinearLayout)this.findViewById(R.id.login_screen);
        username = (EditText) this.findViewById(R.id.login_username);
        username.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_GO)
                {
                    login();
                    return true;
                }
                return false;
            }
        });

        loginButton  = (Button) this.findViewById(R.id.login_button);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                login();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void login() {
        String username = this.username.getText().toString();
        if(Contact.checkUsername(username)) {
            populateDatabase(username);

            // start activity
            Intent routingActivity = new Intent(LoginScreen.this, RoutingActivity.class );
            startActivity(routingActivity);
            finish();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.login_bad_username)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    /**
     * Insert a few explaining messages and the Marlinski user into the database
     * @param username the selected username to create
     */
    private void populateDatabase(String username) {
        // create default public group
        Group defaultPublicGroup = Group.getDefaultGroup();
        DatabaseFactory.getGroupDatabase(this).insertGroup(defaultPublicGroup);

        // create Marlinski user
        Contact marlinski = new Contact(RUMBLE_AUTHOR_NAME, "/Marlinski/=", false);
        DatabaseFactory.getContactDatabase(this).insertOrUpdateContact(marlinski);
        long contactDBID = DatabaseFactory.getContactDatabase(this).getContactDBID(marlinski.getUid());
        long groupDBID = DatabaseFactory.getGroupDatabase(this).getGroupDBID(defaultPublicGroup.getGid());
        DatabaseFactory.getContactJoinGroupDatabase(this).insertContactGroup(contactDBID, groupDBID);

        // add few helping messages
        long time = System.currentTimeMillis();
        PushStatus message4 = new PushStatus(marlinski, defaultPublicGroup, getResources().getString(R.string.swipe_down),time,marlinski.getUid());
        message4.setTimeOfExpiration(0);
        DatabaseFactory.getPushStatusDatabase(this).insertStatus(message4);

        time = System.currentTimeMillis();
        PushStatus message3 = new PushStatus(marlinski, defaultPublicGroup, getResources().getString(R.string.swipe_right),time,marlinski.getUid());
        message3.setTimeOfExpiration(0);
        DatabaseFactory.getPushStatusDatabase(this).insertStatus(message3);

        time = System.currentTimeMillis();
        PushStatus message2 = new PushStatus(marlinski, defaultPublicGroup, getResources().getString(R.string.swipe_left),time,marlinski.getUid());
        message2.setTimeOfExpiration(0);
        DatabaseFactory.getPushStatusDatabase(this).insertStatus(message2);

        time = System.currentTimeMillis();
        PushStatus message1 = new PushStatus(marlinski, defaultPublicGroup, getResources().getString(R.string.welcome_notice),time,marlinski.getUid());
        message1.setTimeOfExpiration(0);
        DatabaseFactory.getPushStatusDatabase(this).insertStatus(message1);

        time = System.currentTimeMillis();
        ChatMessage message5 = new ChatMessage(marlinski, getResources().getString(R.string.chat_message_tuto), time, time, RumbleProtocol.protocolID);
        DatabaseFactory.getChatMessageDatabase(this).insertMessage(message5);

        // create user
        Contact localContact = Contact.createLocalContact(username);
        DatabaseFactory.getContactDatabase(this).insertOrUpdateContact(localContact);

        // user join default group
        contactDBID = DatabaseFactory.getContactDatabase(this).getContactDBID(localContact.getUid());
        groupDBID = DatabaseFactory.getGroupDatabase(this).getGroupDBID(defaultPublicGroup.getGid());
        DatabaseFactory.getContactJoinGroupDatabase(this).insertContactGroup(contactDBID,groupDBID);
    }
}
