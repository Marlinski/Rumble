package org.disrupted.rumble.database.objects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Marlinski
 */
public class InterestVector {

    protected Set<String> joinedGroupIDs;
    protected Map<String, Integer>  hashtagInterests;

    public InterestVector() {
        joinedGroupIDs = new HashSet<String>();
        hashtagInterests = new HashMap<String, Integer>();
    }

    public void addGroup(String groupID) {
        joinedGroupIDs.add(groupID);
    }

    public void addTagInterest(String hashtag, int levelOfInterest) {
        hashtagInterests.put(hashtag, levelOfInterest);
    }

    public final Set<String> getJoinedGroupIDs() {
        return joinedGroupIDs;
    }

    public final Map<String, Integer> getHashtagInterests() {
        return hashtagInterests;
    }

}
