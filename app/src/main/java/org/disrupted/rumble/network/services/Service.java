package org.disrupted.rumble.network.services;

import org.disrupted.rumble.network.linklayer.LinkLayerConnection;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.Worker;
import org.disrupted.rumble.network.services.exceptions.ServiceNotStarted;
import org.disrupted.rumble.network.services.exceptions.WorkerAlreadyBinded;
import org.disrupted.rumble.network.services.exceptions.WorkerNotBinded;

/**
 * @author Marlinski
 */
public interface Service {

    public void bind(ProtocolWorker worker) throws WorkerAlreadyBinded, ServiceNotStarted;

    public void unbind(ProtocolWorker worker) throws WorkerNotBinded;
}
