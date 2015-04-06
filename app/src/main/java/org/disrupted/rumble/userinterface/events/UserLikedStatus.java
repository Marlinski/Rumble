package org.disrupted.rumble.userinterface.events;

import org.disrupted.rumble.message.StatusMessage;

/**
 * @author Marlinski
 */
public class UserLikedStatus extends UserInteractionEvent {

    public String uuid;

    public UserLikedStatus(String uuid) {
        this.uuid = uuid;
    }

}