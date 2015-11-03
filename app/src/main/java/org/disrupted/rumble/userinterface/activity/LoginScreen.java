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

package org.disrupted.rumble.userinterface.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.util.HashUtil;

/**
 * @author Marlinski
 */
public class LoginScreen extends Activity implements View.OnClickListener{

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
        loginButton  = (Button) this.findViewById(R.id.login_button);
        loginButton.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        String username = this.username.getText().toString();
        if(!username.equals("")) {
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
            PushStatus message2 = new PushStatus(marlinski, defaultPublicGroup,getResources().getString(R.string.swipe_left),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message2);

            time = System.currentTimeMillis();
            PushStatus message3 = new PushStatus(marlinski, defaultPublicGroup,getResources().getString(R.string.swipe_right),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message3);

            time = System.currentTimeMillis();
            PushStatus message4 = new PushStatus(marlinski, defaultPublicGroup,getResources().getString(R.string.swipe_down),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message4);

            time = System.currentTimeMillis();
            PushStatus message1 = new PushStatus(marlinski, defaultPublicGroup,getResources().getString(R.string.welcome_notice),time,marlinski.getUid());
            DatabaseFactory.getPushStatusDatabase(this).insertStatus(message1);

            // create user
            Contact localContact = Contact.createLocalContact(username);
            DatabaseFactory.getContactDatabase(this).insertOrUpdateContact(localContact);

            // user join default group
            contactDBID = DatabaseFactory.getContactDatabase(this).getContactDBID(localContact.getUid());
            groupDBID = DatabaseFactory.getGroupDatabase(this).getGroupDBID(defaultPublicGroup.getGid());
            DatabaseFactory.getContactJoinGroupDatabase(this).insertContactGroup(contactDBID,groupDBID);

            // do not show loginscreen next time
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(getString(R.string.pref_previously_started), true);
            ed.commit();

            // start activity
            Intent routingActivity = new Intent(LoginScreen.this, RoutingActivity.class );
            startActivity(routingActivity);
            finish();
        }
    }
}
