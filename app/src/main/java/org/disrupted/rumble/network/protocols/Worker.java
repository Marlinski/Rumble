package org.disrupted.rumble.network.protocols;

/**
 * @author Marlinski
 */
public interface Worker {

    public String getWorkerIdentifier();

    public String getProtocolIdentifier();

    public String getLinkLayerIdentifier();

    public void cancelWorker();

    public void startWorker();

    public void stopWorker();

    public boolean isWorking();

}
