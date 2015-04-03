package org.disrupted.rumble.network.protocols;

import org.disrupted.rumble.network.protocols.command.Command;

/**
 * @author Marlinski
 */
public interface CommandExecutor {

    public boolean execute(Command command);

}
