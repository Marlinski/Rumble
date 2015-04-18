package org.disrupted.rumble.network.services.push;

import android.util.Log;

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
import org.disrupted.rumble.network.protocols.events.ContactInformationReceived;
import org.disrupted.rumble.network.protocols.events.NeighbourConnected;
import org.disrupted.rumble.network.protocols.events.NeighbourDisconnected;
import org.disrupted.rumble.network.protocols.ProtocolWorker;
import org.disrupted.rumble.network.protocols.command.CommandSendLocalInformation;
import org.disrupted.rumble.network.protocols.command.CommandSendPushStatus;
import org.disrupted.rumble.network.protocols.rumble.RumbleProtocol;
import org.disrupted.rumble.util.HashUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import de.greenrobot.event.EventBus;

/**
 * @author Marlinski
 */
public class PushService {

    private static final String TAG = "PushService";

    private static final Object lock = new Object();
    private static PushService instance;

    private static ReplicationDensityWatcher rdwatcher;
    private static final Random random = new Random();

    private static Map<String, MessageDispatcher> workerIdentifierTodispatcher;

    private PushService() {
        rdwatcher = new ReplicationDensityWatcher(1000*3600);
    }

    public static void startService() {
        if(instance != null)
            return;

        synchronized (lock) {
            Log.d(TAG, "[.] Starting PushService");
            if (instance == null) {
                instance = new PushService();
                rdwatcher.start();
                workerIdentifierTodispatcher = new HashMap<String, MessageDispatcher>();
                EventBus.getDefault().register(instance);
            }
        }
    }

    public static void stopService() {
        if(instance == null)
                return;
        synchronized (lock) {
            Log.d(TAG, "[-] Stopping PushService");
            if(EventBus.getDefault().isRegistered(instance))
                EventBus.getDefault().unregister(instance);

            for(Map.Entry<String, MessageDispatcher> entry : instance.workerIdentifierTodispatcher.entrySet()) {
                MessageDispatcher dispatcher = entry.getValue();
                dispatcher.interrupt();
            }
            instance.workerIdentifierTodispatcher.clear();
            rdwatcher.stop();
            instance = null;
        }
    }

    // todo: register protocol to service
    public void onEvent(NeighbourConnected event) {
        if(instance != null) {
            if(!event.worker.getProtocolIdentifier().equals(RumbleProtocol.protocolID))
                return;
            synchronized (lock) {
                MessageDispatcher dispatcher = instance.workerIdentifierTodispatcher.get(event.worker.getWorkerIdentifier());
                if (dispatcher != null) {
                    Log.e(TAG, "worker already binded ?!");
                    return;
                }
                dispatcher = new MessageDispatcher(event.worker);
                instance.workerIdentifierTodispatcher.put(event.worker.getWorkerIdentifier(), dispatcher);
                dispatcher.startDispatcher();
            }
        }
    }

    public void onEvent(NeighbourDisconnected event) {
        if(instance != null) {
            if(!event.worker.getProtocolIdentifier().equals(RumbleProtocol.protocolID))
                return;
            synchronized (lock) {
                MessageDispatcher dispatcher = instance.workerIdentifierTodispatcher.get(event.worker.getWorkerIdentifier());
                if (dispatcher == null)
                    return;
                dispatcher.stopDispatcher();
                instance.workerIdentifierTodispatcher.remove(event.worker.getWorkerIdentifier());
            }
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

        private ProtocolWorker     worker;
        private Contact            contact;

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

        public MessageDispatcher(ProtocolWorker worker) {
            this.running = false;
            this.worker = worker;
            this.contact = null;
            this.max = null;
            this.threshold = 0;
            statuses = new ArrayList<Integer>();
        }

        public void startDispatcher() {
            running = true;
            EventBus.getDefault().register(MessageDispatcher.this);
            sendLocalPreferences(Contact.FLAG_TAG_INTEREST | Contact.FLAG_GROUP_LIST);
            start();
        }

        public void stopDispatcher() {
            running = false;
            this.interrupt();
            worker = null;
            if(EventBus.getDefault().isRegistered(this))
                EventBus.getDefault().unregister(this);
        }

        private void updateStatusList() {
            if(contact == null)
                return;
            PushStatusDatabase.StatusQueryOption options = new PushStatusDatabase.StatusQueryOption();
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_GROUP;
            options.groupIDFilters = contact.getJoinedGroupIDs();
            options.filterFlags |= PushStatusDatabase.StatusQueryOption.FILTER_NEVER_SEND_TO_USER;
            options.interfaceID = HashUtil.computeInterfaceID(
                    worker.getLinkLayerConnection().getRemoteLinkLayerAddress(),
                    worker.getProtocolIdentifier());
            options.query_result = PushStatusDatabase.StatusQueryOption.QUERY_RESULT.LIST_OF_DBIDS;
            DatabaseFactory.getPushStatusDatabase(RumbleApplication.getContext()).getStatuses(options, onStatusLoaded);
        }
        DatabaseExecutor.ReadableQueryCallback onStatusLoaded = new DatabaseExecutor.ReadableQueryCallback() {
            @Override
            public void onReadableQueryFinished(Object result) {
                if (result != null) {
                    try {
                        takeLock.lock();
                        Log.d(TAG, "[+] update status list");
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
                    // pickup a message and send it to the CommandExecutor
                    if (worker != null) {
                        PushStatus message = pickMessage();
                        worker.execute(new CommandSendPushStatus(message));
                        message.discard();
                        //todo just for the sake of debugging
                        //sleep(1000, 0);
                    }

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
            try {
                putlock.lock();

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
                statuses.remove(new Integer(i));
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
                            //Log.d(TAG, "cannot retrieve statusId: "+id);
                            statuses.remove(new Integer((int)id));
                            continue;
                        }

                        // get max probability Pmax
                        float maxScore = computeScore(max, contact);

                        // get element probability Pu
                        float score = computeScore(pickedUpMessage, contact);

                        if (score <= threshold) {
                            statuses.remove(new Integer((int)id));
                            pickedUpMessage.discard();
                            pickedUpMessage = null;
                            continue;
                        }

                        int shallwepick = random.nextInt((int) (maxScore * 1000));
                        if (shallwepick <= (score * 1000)) {
                            statuses.remove(new Integer((int)pickedUpMessage.getdbId()));
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

        /*
         * Event management
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
            PushStatus message = new PushStatus(event.status);
            add(message);
            message.discard();
        }

        /*
         * this event only bind the contact uid with the interface
         * we wait for the related DatabaseEvent (if any) for updating the status list
         */
        public void onEvent(ContactInformationReceived info) {
            if(info.sender.equals(worker.getLinkLayerConnection().getRemoteLinkLayerAddress())) {
                if(this.contact == null)
                    this.contact = new Contact(info.contact);
            }
        }
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

        public void sendLocalPreferences(int flags) {
            Contact local = Contact.getLocalContact();
            CommandSendLocalInformation command = new CommandSendLocalInformation(local,flags);
            worker.execute(command);
        }
    }
}
