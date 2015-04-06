package org.disrupted.rumble.userinterface.events;

import org.disrupted.rumble.message.StatusMessage;

/**
 * @author Marlinski
 */
public class UserSavedStatus extends UserInteractionEvent {

    public String uuid;

    public UserSavedStatus(String uuid) {
        this.uuid = uuid;
    }

}