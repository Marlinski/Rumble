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

package org.disrupted.rumble;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import org.disrupted.rumble.contact.Contact;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;

/**
 * @author Marlinski
 */
public class LoginScreen extends Activity implements View.OnClickListener{

    private static final String TAG = "LoginScreen";

    private Button loginButton;
    private TextView usernameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.login_layout);

        usernameView = (TextView) this.findViewById(R.id.login_username);
        loginButton = (Button) this.findViewById(R.id.login_button);
        loginButton.setOnClickListener(this);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        String username = usernameView.getText().toString();
        if(username != "") {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
            SharedPreferences.Editor ed = prefs.edit();
            ed.putBoolean(getString(R.string.pref_previously_started), true);
            ed.commit();

            Contact localContact = new Contact(username, "", true);
            DatabaseFactory.getContactDatabase(this).insertContact(localContact, callback);
        }
    }

    private DatabaseExecutor.WritableQueryCallback callback = new DatabaseExecutor.WritableQueryCallback() {
        @Override
        public void onWritableQueryFinished(boolean success) {
            if(success)
                runOnUiThread(new Runnable() {
                                  @Override
                                  public void run() {
                                      Intent routingActivity = new Intent(LoginScreen.this, RoutingActivity.class );
                                      startActivity(routingActivity);
                                      finish();
                                  }
                              });
        }
    };

}
