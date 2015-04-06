package org.disrupted.rumble.userinterface.events;

import org.disrupted.rumble.message.StatusMessage;

/**
 * @author Marlinski
 */
public class UserReadStatus extends UserInteractionEvent {

    public String uuid;

    public UserReadStatus(String uuid) {
        this.uuid = uuid;
    }

}