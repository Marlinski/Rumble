package org.disrupted.rumble.network.protocols.events;

import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.events.NetworkEvent;

/**
 * This event holds every information known on a received transmission that happened successfully.
 * These information includes:
 *
 * - The received status (as it was received)
 * - The name of the author as 'claimed' by the sender (see CacheManager for policy)
 * - The name of the group as 'claimed' by the sender (see CacheManager for policy)
 * - The Base64 ID of the sender
 * - The protocol used to transmit this status (rumble, firechat)
 * - The link layer used (bluetooth, wifi)
 * - The size of the data transmitted (bytes)
 * - The duration of the transmission (ms)
 *
 * These information will be used by different component to update some informations :
 *  - The CacheManager to update its list and the neighbour's queue as well
 *  - The LinkLayerAdapter to update its internal metric that is used by getBestInterface
 *  - The FragmentStatusList to provide a visual feedback to the user
 *
 * @author Lucien Loiseau
 */
public class PushStatusReceived extends NetworkEvent{

    public PushStatus status;
    public String gid;
    public String senderID;
    public String tempfile;
    public String protocolID;
    public String linkLayerIdentifier;

    public PushStatusReceived(PushStatus status, String gid, String sender, String tempfile,
                              String protocolID, String linkLayerIdentifier) {
        this.status = status;
        this.gid = gid;
        this.senderID = sender;
        this.tempfile = tempfile;
        this.protocolID = protocolID;
        this.linkLayerIdentifier = linkLayerIdentifier;
    }

    @Override
    public String shortDescription() {
        if(status != null)
            return status.getPost()+" ("+status.getAuthor().getName()+")";
        else
            return "";
    }
}
