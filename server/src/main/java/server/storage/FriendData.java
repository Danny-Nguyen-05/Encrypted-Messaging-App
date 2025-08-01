package server.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a user and their confirmed friends list.
 */
public class FriendData {
    private String username;
    private List<String> friends;

    /**
     * Default constructor for JSON deserialization.
     */
    public FriendData() {
        this.friends = new ArrayList<>();
    }

    /**
     * Construct a new FriendData record for the given username.
     */
    public FriendData(String username) {
        this.username = username;
        this.friends = new ArrayList<>();
    }

    /**
     * @return the user’s username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Set the user’s username (used by JSON deserializer).
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the list of confirmed friends
     */
    public List<String> getFriends() {
        return friends;
    }

    /**
     * Set the confirmed friends list (used by JSON deserializer).
     */
    public void setFriends(List<String> friends) {
        this.friends = friends;
    }
}
