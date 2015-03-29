package org.disrupted.rumble.database.events;

/**
 * @author Marlinski
 */
public class NewGroupEvent {

    private String name;

    public NewGroupEvent(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
