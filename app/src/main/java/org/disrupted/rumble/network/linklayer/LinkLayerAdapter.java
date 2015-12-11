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

package org.disrupted.rumble.network.linklayer;

/**
 * LinkLayerAdapter is an Interface that is responsible of managing a LinkLayer interface such
 * as Bluetooth or Wifi. It is directly under the responsibility of NetworkCoordinator and
 * all the LinkLayerAdapter methods are called from it.
 *
 * @author Lucien Loiseau
 */
public interface LinkLayerAdapter {

    public boolean isActivated();

    public String getLinkLayerIdentifier();

    public void linkStart();

    public void linkStop();

}
