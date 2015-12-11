package org.disrupted.rumble.network.services.push;

import org.disrupted.rumble.util.Log;

import org.disrupted.rumble.app.RumbleApplication;
import org.disrupted.rumble.database.DatabaseExecutor;
import org.disrupted.rumble.database.DatabaseFactory;
import org.disrupted.rumble.database.PushStatusDatabase;
import org.disrupted.rumble.database.events.ContactGroupListUpdated;
import org.disrupted.rumble.database.events.ContactTagInterestUpdatedEvent;
import org.disrupted.rumble.database.events.StatusDeletedEvent;
import org.disrupted.rumble.database.events.StatusInsertedEvent;
import org.disrupted.rumble.database.objects.Contact;
import org.disrupted.rumble.database.objects.PushStatus;
import org.disrupted.rumble.network.NetworkCoordinator;
import org.disrupted.rumble.network.protocols.ProtocolChannel;
import org.disrupted.rumble.network.protocols.command.Command;
import org.disrupted.rumble.network.protocols.events.CommandExecuted;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.events.ChannelConnected;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.network.services.ServiceLayer;
import org.disrupted.rumble.network.events.ContactDisconnected;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Lucien Loiseau
 */
public class PushService implements ServiceLayer {

    private static final String TAG = "PushService";

    private static final Object lock = new Object();
    private static PushService instance;

    private static ReplicationDensityWatcher rdwatcher;
    private static final Random random = new Random();

    private static NetworkCoordinator networkCoordinator;

    private static Map<Contact, MessageDispatcher> contactToDispatcher;

    public static PushService getInstance(NetworkCoordinator networkCoordinator) {
        synchronized (lock) {
            if(instance == null)
                instance = new PushService(networkCoordinator);

            return instance;
        }
    }
    private PushService(NetworkCoordinator networkCoordinator) {
        this.networkCoordinator = networkCoordinator;
        rdwatcher = new ReplicationDensityWatcher(1000*3600);
    }

    @Override
    public String getServiceIdentifier() {
        return TAG;
    }

    public void startService() {
        synchronized (lock) {
            Log.d(TAG, "[+] Starting PushService");
            rdwatcher.start();
            contactToDispatcher = new HashMap<Contact, MessageDispatcher>();
            EventBus.getDefault().register(this);
        }
    }

    public void stopService() {
        synchronized (lock) {
            Log.d(TAG, "[-] Stopping PushService");
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);

            for(Map.Entry<Contact, MessageDispatcher> entry : contactToDispatcher.entrySet()) {
                MessageDispatcher dispatcher = entry.getValue();
                dispatcher.interrupt();
            }
            contactToDispatcher.clear();
            rdwatcher.stop();
        }
    }

    /*
     * Whenever  a new neighbour  is connected, we send him  our contact description
     * If the  other end do the same, that should  trigger a ContactConnected event
     * unless this contact has already been connected in which case this would allow
     * to add a new channel of communication
     */
    public void onEvent(ChannelConnected event) {
        if(!event.channel.getProtocolIdentifier().equals(RumbleProtocol.protocolID))
            return;

        Contact local = Contact.getLocalContact();
        CommandSendLocalInformation command = new CommandSendLocalInformation(local,Contact.FLAG_TAG_INTEREST | Contact.FLAG_GROUP_LIST);
        event.channel.executeNonBlocking(command);
    }

    /*
     * Whenever a new contact is connected (i.e. we received a contact information packet),
     * we start the dispatcher that will send him the PushStatus according to its preferences.
     */
    public void onEvent(ContactInformationReceived event) {
        if(!event.channel.getProtocolIdentifier().equals(RumbleProtocol.protocolID))
            return;

        synchronized (lock) {
            MessageDispatcher dispatcher = contactToDispatcher.get(event.contact);
            if (dispatcher != null) {
                Log.d(TAG, "A dispatcher contact "+event.contact.getName()
                        +" ("+event.contact.getUid()+") already exists");
                return;
            }
            dispatcher = new MessageDispatcher(event.contact);
            dispatcher.startDispatcher();
        }
    }

    /*
     * Whenever a contact is disconnected, it means that every channel has been closed.
     * We can thus stop the dispatcher.
     */
    public void onEvent(ContactDisconnected event) {
        synchronized (lock) {
            MessageDispatcher dispatcher = contactToDispatcher.get(event.contact);
            if (dispatcher == null)
                return;
            dispatcher.stopDispatcher();
        }
    }

    private static float computeScore(PushStatus message, Contact contact) {
        if(!contact.getJoinedGroupIDs().contains(message.getGroup().getGid()))
            return 0;

        float relevance;
        int totalInterest  = 0;
        int totalHashtag   = 0;
        for(String hashtag : message.getHashtagSet()) {
            Integer value = contact.getHashtagInterests().get(hashtag);
            if(value != null) {
                totalInterest += value;
                totalHashtag++;
            }
        }
        if(totalHashtag > 0)
            relevance = totalInterest/(totalHashtag*Contact.MAX_INTEREST_TAG_VALUE);
        else
            relevance = 0;
        float replicationDensity = rdwatcher.computeMetric(message.getUuid());
        float quality =  (message.getDuplicate() == 0) ? 0 : (float)message.getLike()/(float)message.getDuplicate();
        float age = (message.getTTL() <= 0) ? 1 : (1- (System.currentTimeMillis() - message.getTimeOfCreation())/message.getTTL());
        boolean distance = true;

        float a = 0;
        float b = (float)0.6;
        float c = (float)0.4;

        float score = (a*relevance + b*replicationDensity + c*quality)*age*(distance ? 1 : 0);

        return score;
    }

    // todo: not being dependant on age would make it so much easier ....
    private static class MessageDispatcher extends Thread {

        private static final String TAG = "MessageDispatcher";

        private Contact            contact;
        private ProtocolChannel    tmpchannel;

        private ArrayList<Integer> statuses;
        private float threshold;

        // locks for managing the ArrayList
        private final ReentrantLock putLock = new ReentrantLock(true);
        private final ReentrantLock takeLock = new ReentrantLock(true);
        private final Condition notEmpty = takeLock.newCondition();
        private boolean running;

        private PushStatus max;

        private void fullyLock() {
            putLock.lock();
            takeLock.lock();
        }
        private void fullyUnlock() {
            putLock.unlock();
            takeLock.unlock();
        }
        private void signalNotEmpty() {
            final ReentrantLock takeLock = this.takeLock;
            takeLock.lock();
            try {
                notEmpty.signal();
            } finally {
                takeLock.unlock();
            }
        }

        public MessageDispatcher(Contact contact) {
            this.running = false;
            this.contact = contact;
            this.max = null;
            this.threshold = 0;
            statuses = new ArrayList<Integer>();
            contactToDispatcher.put(contact, this);
        }

        public void startDispatcher() {
            running = true;
            EventBus.getDefault().register(MessageDispatcher.this);
            start();
        }

        public void stopDispatcher() {
            running = false;
            this.interrupt();
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
            contactToDispatcher.remove(contact);
        }

        private void updateStatusList() {
            if(contact == null)
                return;
            PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_NOT_EXPIRED;
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_GROUP;
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_NEVER_SEND_TO_USER;
            options.groupIDFilters = contact.getJoinedGroupIDs();
            options.uid = contact.getUid();
            options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_DBIDS;
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatuses(options, onStatusLoaded);
        }
        DatabaseExecutor.ReadableQueryCallback onStatusLoaded = new DatabaseExecutor.ReadableQueryCallback() {
            @Override
            public void onReadableQueryFinished(Object result) {
                if (result != null) {
                    try {
                        takeLock.lock();
                        Log.d(TAG, "[+] update status list: "+result.toString());
                        statuses.clear();
                        final ArrayList<Integer> answer = (ArrayList<Integer>)result;
                        for (Integer s : answer) {
                            PushStatus message = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext())
                                    .getStatus(s);
                            if(message != null)
                                add(message);
                        }
                        // the "max" has been automatically updated while we were adding items
                    } finally {
                        takeLock.unlock();
                    }
                }
            }
        };

        @Override
        public void run() {
            try {
                Log.d(TAG, "[+] MessageDispatcher initiated");
                do {
                        // pick a message randomly
                        PushStatus message = pickMessage();

                        // prepare the command
                        Command cmd = new CommandSendPushStatus(message);

                        // choose a channel to execute the command
                        ProtocolChannel channel = PushService.networkCoordinator.neighbourManager.chooseBestChannel(contact);
                        this.tmpchannel = channel;
                        if(this.tmpchannel == null) {
                            // the contact must have disconnected completely
                            stopDispatcher();
                            break;
                        }

                        // send the message (blocking operation)
                        if(channel.execute(cmd)) {
                            if(max.equals(message))
                                max = null;
                            statuses.remove(Integer.valueOf((int) message.getdbId()));
                        }

                        message.discard();
                } while (running);

            } catch (InterruptedException ie) {
            } finally {
                clear();
                Log.d(TAG, "[-] MessageDispatcher stopped");
            }
        }

        private void clear() {
            fullyLock();
            try {
                if(EventBus.getDefault().isRegistered(this))
                    EventBus.getDefault().unregister(this);
                statuses.clear();
            } finally {
                fullyUnlock();
            }
        }

        private boolean add(PushStatus message){
            if(this.contact == null)
                return false;
            final ReentrantLock putlock = this.putLock;
            putlock.lock();
            try {

                float score = computeScore(message, contact);

                if (score <= threshold) {
                    message.discard();
                    return false;
                }

                statuses.add((int)message.getdbId());

                /* we update the max value */
                if (max == null) {
                    max = message;
                } else {
                    float maxScore = computeScore(max, contact);
                    if (score > maxScore) {
                        max.discard();
                        max = message;
                    } else
                        message.discard();
                }

                signalNotEmpty();
                return true;
            } finally {
                putlock.unlock();
            }
        }

        // /!\  carefull, it does not lock thread
        private void updateMax() {
            float maxScore = 0;

            /*
             * we only update the max if it has been sent (max == null) or it is no longer valid
             */
            if(max != null) {
                maxScore = computeScore(max, contact);
                if(maxScore > threshold)
                    return;
                max.discard();
                max = null;
            }

            ArrayList<Integer> toDelete = new ArrayList<Integer>();
            for(Integer id : statuses) {
                PushStatus message = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext())
                        .getStatus(id);
                float score = computeScore(message, contact);

                // we delete the message if it is no longer valid (for instance it expired)
                if(score <= threshold) {
                    message.discard();
                    toDelete.add((int)message.getdbId());
                    continue;
                }

                // item becomes the new max if max was null
                if(max == null) {
                    max = message;
                    maxScore = score;
                    continue;
                }

                // if we have better score than the max, we replace it
                if(score > maxScore) {
                    max.discard();
                    max = message;
                    maxScore = score;
                } else {
                    // we get rid of the message as we only stores message ids
                    message.discard();
                }
            }
            for(Integer i : toDelete) {
                statuses.remove(Integer.valueOf(i));
            }
        }

        /*
         *  See the paper:
         *  "Roulette-wheel selection via stochastic acceptance"
         *  By Adam Lipowski, Dorota Lipowska
         */
        private PushStatus pickMessage() throws InterruptedException {
            final ReentrantLock takelock = this.takeLock;
            final ReentrantLock putlock = this.takeLock;

            boolean pickup = false;
            PushStatus pickedUpMessage;
            takelock.lockInterruptibly();
            try {
                do {
                    while (statuses.size() == 0)
                        notEmpty.await();

                    putlock.lock();
                    try {
                        updateMax();

                        // randomly pickup an element homogeneously
                        int index = random.nextInt(statuses.size());
                        long id = statuses.get(index);
                        pickedUpMessage = DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatus(id);
                        if(pickedUpMessage == null) {
                            statuses.remove(Integer.valueOf((int)id));
                            continue;
                        }

                        // get max probability Pmax and element probability Pu
                        float maxScore = computeScore(max, contact);
                        float score = computeScore(pickedUpMessage, contact);

                        if (score <= threshold) {
                            statuses.remove(Integer.valueOf((int)id));
                            pickedUpMessage.discard();
                            pickedUpMessage = null;
                            continue;
                        }

                        int shallwepick = random.nextInt((int) (maxScore * 1000));
                        if (shallwepick <= (score * 1000)) {
                            pickup = true;
                        } else {
                            pickedUpMessage.discard();
                        }
                    } finally {
                        putlock.unlock();
                    }
                } while(!pickup);
            } finally {
                takelock.unlock();
            }
            return pickedUpMessage;
        }

        public void sendLocalPreferences(int flags) {
            Contact local = Contact.getLocalContact();
            CommandSendLocalInformation command = new CommandSendLocalInformation(local,flags);

            ProtocolChannel channel = PushService.networkCoordinator.neighbourManager.chooseBestChannel(contact);
            this.tmpchannel = channel;
            if(this.tmpchannel == null) {
                // the contact must have disconnected
                stopDispatcher();
                return;
            }
            channel.executeNonBlocking(command);
        }


        // ====================== Event management ==========================

        /*
         * Keeping the list of status to push up-to-date
         */
        public void onEvent(StatusDeletedEvent event) {
            fullyLock();
            try {
                statuses.remove(Integer.valueOf((int) event.dbid));
            } finally {
                fullyUnlock();
            }
        }
        public void onEvent(StatusInsertedEvent event) {
            if(!event.status.getAuthor().equals(this.contact) &&
               !event.status.receivedBy().equals(this.contact.getUid())) {
                PushStatus message = new PushStatus(event.status);
                add(message);
                message.discard();
            }
        }

        /*
         * we don't send any status until we received an Interest Vector
         *
         * this event only bind the contact uid with the interface
         * we wait for the related DatabaseEvent (if any) for updating the status list
         */
        public void onEvent(ContactGroupListUpdated event) {
            if(this.contact == null)
                return;
            if(event.contact.equals(this.contact)) {
                this.contact.setJoinedGroupIDs(event.contact.getJoinedGroupIDs());
                updateStatusList();
            }
            if(event.contact.isLocal()) {
                sendLocalPreferences(Contact.FLAG_GROUP_LIST);
            }
        }
        public void onEvent(ContactTagInterestUpdatedEvent event) {
            if(this.contact == null)
                return;
            if(event.contact.equals(this.contact)) {
                this.contact.setHashtagInterests(event.contact.getHashtagInterests());
            }
            if(event.contact.isLocal()) {
                sendLocalPreferences(Contact.FLAG_TAG_INTEREST);
            }
        }

    }
}
