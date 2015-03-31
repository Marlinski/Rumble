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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.disrupted.rumble.R;
import org.disrupted.rumble.network.NeighbourInfo;
import org.disrupted.rumble.network.linklayer.LinkLayerNeighbour;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothLinkLayerAdapter;
import org.disrupted.rumble.network.linklayer.bluetooth.BluetoothNeighbour;
import org.disrupted.rumble.network.linklayer.wifi.WifiManagedLinkLayerAdapter;
import org.disrupted.rumble.network.protocols.ProtocolNeighbour;

import java.util.Iterator;
import java.util.List;

/**
 * @author Marlinski
 */
public class NeighborhoodListAdapter extends BaseAdapter implements View.OnClickListener {

    private static final String TAG = "NeighborListAdapter";

    private final LayoutInflater inflater;
    private List<NeighbourInfo> neighborhood;

    public NeighborhoodListAdapter(Activity activity, List<NeighbourInfo> neighborhood) {
        this.inflater     = LayoutInflater.from(activity);
        this.neighborhood = neighborhood;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        NeighbourInfo entry = neighborhood.get(i);

        View neighborView = inflater.inflate(R.layout.neighbour_item, null, true);
        TextView name = (TextView) neighborView.findViewById(R.id.neighbour_item_name);
        TextView id = (TextView) neighborView.findViewById(R.id.neighbour_item_link_layer_name);
        ImageView bluetoothIcon = (ImageView) neighborView.findViewById(R.id.neighbour_item_bluetooth);
        ImageView wifiIcon = (ImageView) neighborView.findViewById(R.id.neighbour_item_wifi);

        if(entry.neighbour.getLinkLayerIdentifier().equals(BluetoothLinkLayerAdapter.LinkLayerIdentifier)) {
            bluetoothIcon.setVisibility(View.VISIBLE);
            if(entry.isConnected())
                bluetoothIcon.setImageResource(R.drawable.ic_bluetooth_white_18dp);
            else
                bluetoothIcon.setImageResource(R.drawable.ic_bluetooth_grey600_18dp);
            BluetoothNeighbour btneighbour = (BluetoothNeighbour)(entry.neighbour);
            name.setText(btneighbour.getBluetoothDeviceName());
            id.setText(btneighbour.getLinkLayerAddress());
        } else {
            bluetoothIcon.setVisibility(View.INVISIBLE);
        }

        if(entry.neighbour.getLinkLayerIdentifier().equals(WifiManagedLinkLayerAdapter.LinkLayerIdentifier)) {
            wifiIcon.setVisibility(View.VISIBLE);
        } else {
            wifiIcon.setVisibility(View.INVISIBLE);
        }

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

    public void updateList(List<NeighbourInfo> newNeighborhood) {
        this.neighborhood.clear();
        this.neighborhood = newNeighborhood;
        notifyDataSetChanged();
    }

    public void reset() {
        this.neighborhood.clear();
        notifyDataSetChanged();
    }
}

