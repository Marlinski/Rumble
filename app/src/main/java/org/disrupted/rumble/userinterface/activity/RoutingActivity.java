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
import android.view.Window;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.DatabaseFactory;

/**
 * @author Marlinski
 */
public class RoutingActivity extends Activity {

    private static final String TAG = "RoutingActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        if(DatabaseFactory.getContactDatabase(this).getLocalContact() != null) {
            Intent homeActivity = new Intent(this, HomeActivity.class );
            startActivity(homeActivity);
            finish();
        } else {
            Intent loginScreen = new Intent(this, LoginScreen.class );
            startActivity(loginScreen);
            finish();
        }
    }


}
