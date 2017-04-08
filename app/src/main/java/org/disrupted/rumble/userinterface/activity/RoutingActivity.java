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

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Window;

import org.disrupted.rumble.R;
import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.util.FileUtil;
import org.disrupted.rumble.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Lucien Loiseau
 */
public class RoutingActivity extends AppCompatActivity {

    private static final String TAG = "RoutingActivity";
    private static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 14;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);

        if(DatabaseFactory.getContactDatabase(this).getLocalContact() != null) {
            if(checkRumblePermission())
                gotoHomeActivity();
        } else {
            Intent loginScreen = new Intent(this, LoginScreen.class );
            startActivity(loginScreen);
            finish();
        }
    }


    private boolean checkRumblePermission() {
        List<String> permissionsNeeded = new ArrayList<String>();

        final List<String> permissionsList = new ArrayList<String>();
        if (!addPermission(permissionsList, Manifest.permission.ACCESS_FINE_LOCATION))
            permissionsNeeded.add("GPS");
        if (!addPermission(permissionsList, Manifest.permission.CAMERA))
            permissionsNeeded.add("Camera");
        if (!addPermission(permissionsList, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            permissionsNeeded.add("Write External Storage");

        if (permissionsList.size() > 0) {
            if (permissionsNeeded.size() > 0) {
                // Need Rationale
                String message = "You need to grant access to " + permissionsNeeded.get(0);
                for (int i = 1; i < permissionsNeeded.size(); i++)
                    message = message + ", " + permissionsNeeded.get(i);
                final Activity activity = this;
                showMessageOKCancel(message,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ActivityCompat.requestPermissions(
                                        activity,
                                        permissionsList.toArray(new String[permissionsList.size()]),
                                        REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
                            }
                        });
                return false;
            }
            ActivityCompat.requestPermissions(this,
                    permissionsList.toArray(new String[permissionsList.size()]),
                    REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
            return false;
        }

        return true;
    }

    private boolean addPermission(List<String> permissionsList, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsList.add(permission);
            // Check for Rationale Option
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission))
                return false;
        }
        return true;
    }

    private void showMessageOKCancel(String message, DialogInterface.OnClickListener okListener) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", null)
                .create()
                .show();
    }

    /*
     * Receive the result of dynamic permission request for storage
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS: {
                // Fill with results
                for (int i = 0; i < permissions.length; i++) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                        Log.d(TAG, "[+] permission for "+permissions[i]+" granted");
                    else
                        Log.d(TAG, "[!] permission for "+permissions[i]+" refused");
                }
            }; break;
            default:
        }
        gotoHomeActivity();
    }

    private void gotoHomeActivity() {
        /*
         * We start NetworkCoordinator.
         * Note: the NetworkCoordinator may already be started (either because of StartOnBoot
         * or simply because the application was already open). Anyway, it should be
         * safe to use startIntent because from the documentation:
         *
         * "If this service is not already running, it will be instantiated and started
         * (creating a process for it if needed); if it is running then it remains running."
         */
        Intent startIntent = new Intent(this, NetworkCoordinator.class);
        startIntent.setAction(NetworkCoordinator.ACTION_START_FOREGROUND);
        startService(startIntent);

        Intent homeActivity = new Intent(this, HomeActivity.class );
        startActivity(homeActivity);
        finish();
    }

}
