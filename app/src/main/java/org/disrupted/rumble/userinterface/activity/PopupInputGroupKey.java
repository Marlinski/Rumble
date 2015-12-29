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

package org.disrupted.rumble.userinterface.activity;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import org.disrupted.rumble.R;
import org.disrupted.rumble.database.objects.Group;
import org.disrupted.rumble.userinterface.events.UserCreateGroup;
import org.disrupted.rumble.userinterface.events.UserJoinGroup;
import org.disrupted.rumble.util.Log;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class PopupInputGroupKey extends Activity {

    private static final String TAG = "PopupCreateGroup";

    private LinearLayout  dismiss;
    private EditText      groupKey;
    private ImageButton   inputGroupButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_popup_input_group_key);

        dismiss              = (LinearLayout)(findViewById(R.id.new_group_dismiss));
        groupKey             = (EditText)(findViewById(R.id.popup_group_key));
        inputGroupButton     = (ImageButton)(findViewById(R.id.popup_button_input_group));

        dismiss.setOnClickListener(onDiscardClick);
        inputGroupButton.setOnClickListener(onInputGroup);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE|
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    View.OnClickListener onDiscardClick = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(groupKey.getWindowToken(), 0);
            finish();
        }
    };

    View.OnClickListener onInputGroup = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            final Activity activity = PopupInputGroupKey.this;
            try {
                if (groupKey.getText().toString().equals(""))
                    return;
                Group group = Group.getGroupFromBase64ID(groupKey.getText().toString());
                if(group == null) {
                    //Snackbar.make(coordinatorLayout, "no group were added", Snackbar.LENGTH_SHORT)
                    //        .show();
                } else {
                    // add Group to database
                    EventBus.getDefault().post(new UserJoinGroup(group));
                    //Snackbar.make(coordinatorLayout, "the group " + group.getName() + " has been added", Snackbar.LENGTH_SHORT)
                    //        .show();
                }
            } catch (Exception e) {
                Log.e(TAG, "[!] " + e.getMessage());
            } finally {
                groupKey.setText("");
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(groupKey.getWindowToken(), 0);
                finish();
            }
        }
    };

}
