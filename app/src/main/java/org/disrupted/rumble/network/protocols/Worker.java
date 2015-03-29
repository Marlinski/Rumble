package org.disrupted.rumble.network.protocols;

/**
 * @author Marlinski
 */
public interface Worker {

    public String getWorkerIdentifier();

    public String getProtocolIdentifier();

    public String getLinkLayerIdentifier();

    public void startWorking();

    public void stopWorking();

    public boolean isWorking();

}
