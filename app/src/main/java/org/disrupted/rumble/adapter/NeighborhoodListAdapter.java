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

package org.disrupted.rumble.adapter;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.network.Neighbour;
import org.disrupted.rumble.network.NeighbourRecord;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.exceptions.RecordNotFoundException;
import org.disrupted.rumble.network.exceptions.UnknownNeighbourException;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;

import java.util.List;

/**
 * @author Marlinski
 */
public class NeighborhoodListAdapter extends BaseAdapter implements View.OnClickListener {

    private static final String TAG = "NeighborListAdapter";

    private final LayoutInflater inflater;
    private List<Neighbour> neighborhood;

    public NeighborhoodListAdapter(Activity activity, List<Neighbour> neighborhood) {
        this.inflater     = LayoutInflater.from(activity);
        this.neighborhood = neighborhood;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View neighborView = inflater.inflate(R.layout.neighbour_item, null, true);
        TextView name = (TextView) neighborView.findViewById(R.id.neighbour_item_name);
        TextView id = (TextView) neighborView.findViewById(R.id.neighbour_item_link_layer_name);
        ImageView bluetoothIcon = (ImageView) neighborView.findViewById(R.id.neighbour_item_bluetooth);
        ImageView wifiIcon = (ImageView) neighborView.findViewById(R.id.neighbour_item_wifi);

        Neighbour neighbour = neighborhood.get(i);
        id.setText(neighbour.getLinkLayerName());
        try {
            name.setText(NetworkCoordinator.getInstance().getNeighbourName(neighbour));

            if (NetworkCoordinator.getInstance().isNeighbourInRange(neighbour, BluetoothLinkLayerAdapter.LinkLayerIdentifier))
                bluetoothIcon.setVisibility(View.VISIBLE);
            if (NetworkCoordinator.getInstance().isNeighbourConnectedLinkLayer(neighbour, BluetoothLinkLayerAdapter.LinkLayerIdentifier))
                bluetoothIcon.setImageResource(R.drawable.ic_bluetooth_white_18dp);
            else
                bluetoothIcon.setImageResource(R.drawable.ic_bluetooth_grey600_18dp);

        } catch(RecordNotFoundException ignore) {
            Log.e(TAG, "[!] record not found !");
        }
        //todo add WiFi

        return neighborView;
    }

    @Override
    public Object getItem(int i) {
        return neighborhood.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public int getCount() {
        return neighborhood.size();
    }

    @Override
    public void onClick(View view) {
    }

    public void updateList(List<Neighbour> newNeighborhood) {
        this.neighborhood.clear();
        this.neighborhood = newNeighborhood;
        notifyDataSetChanged();
    }

    public void reset() {
        this.neighborhood.clear();
        notifyDataSetChanged();
    }
}

