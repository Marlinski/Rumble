package org.disrupted.rumble.network.services;

/**
 * @author Marlinski
 */
public interface ServiceLayer {

    //public void register(String protocolIdentifier);

    //public void unregister(String protocolIdentifier);

    public String getServiceIdentifier();

    public void startService();

    public void stopService();
}
