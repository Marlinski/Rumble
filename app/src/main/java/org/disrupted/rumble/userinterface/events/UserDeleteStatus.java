package org.disrupted.rumble.userinterface.events;

import org.disrupted.rumble.message.StatusMessage;

/**
 * @author Marlinski
 */
public class UserDeleteStatus extends UserInteractionEvent {

    public String uuid;

    public UserDeleteStatus(String uuid) {
        this.uuid = uuid;
    }

}
