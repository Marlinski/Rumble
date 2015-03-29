package org.disrupted.rumble.network.services.push;

import org.disrupted.rumble.network.protocols.ProtocolNeighbour;
import org.disrupted.rumble.network.services.Service;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Marlinski
 */
public class PushService implements Service{

    ProtocolNeighbour neighbour;

    public PushService(ProtocolNeighbour neighbour) {
        this.neighbour = neighbour;
    }

    @Override
    public void startService() {
    }

    @Override
    public void stopService() {
    }

    private class MessageSelector {

        private Set<Long> q;
        private final ReentrantLock lock = new ReentrantLock(true);
        private final Condition notEmpty = lock.newCondition();

        public MessageSelector() {
            q = new TreeSet<Long>();
        }
    }
}
