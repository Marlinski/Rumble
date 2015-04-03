package org.disrupted.rumble.database.events;

/**
 * @author Marlinski
 */
public class GroupInsertedEvent {

    public final String name;

    public GroupInsertedEvent(String name) {
        this.name = name;
    }

}
