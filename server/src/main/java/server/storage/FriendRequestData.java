package server.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the pending friend requests for a given user.
 * 'incoming' = users who have sent requests to this user.
 * 'outgoing' = users to whom this user has sent requests.
 */
public class FriendRequestData {
    private String username;
    private List<String> incoming;
    private List<String> outgoing;

    /**
     * Default constructor for JSON deserialization.
     */
    public FriendRequestData() {
        this.username = null;
        this.incoming = new ArrayList<>();
        this.outgoing = new ArrayList<>();
    }

    /**
     * Create a new record for the given username.
     */
    public FriendRequestData(String username) {
        this.username = username;
        this.incoming = new ArrayList<>();
        this.outgoing = new ArrayList<>();
    }

    /**
     * Returns the username owning this request record.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username (used by JSON deserializer).
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Users who have sent friend requests to this user.
     */
    public List<String> getIncoming() {
        return incoming;
    }

    /**
     * Sets the incoming request list (used by JSON deserializer).
     */
    public void setIncoming(List<String> incoming) {
        this.incoming = incoming;
    }

    /**
     * Users to whom this user has sent friend requests.
     */
    public List<String> getOutgoing() {
        return outgoing;
    }

    /**
     * Sets the outgoing request list (used by JSON deserializer).
     */
    public void setOutgoing(List<String> outgoing) {
        this.outgoing = outgoing;
    }
}