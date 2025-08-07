package server;

import server.storage.FriendData;
import server.storage.FriendRequestData;
import server.storage.DatabaseStore;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/* Manages friend relationships and friend request operations, storing data in MySQL with case-insensitive username handling */
public class FriendManager {
    /* Loads friend relationships from the MySQL database */
    private static List<FriendData> loadFriends() throws SQLException {
        return DatabaseStore.loadFriends();
    }

    /* Saves friend relationships to the MySQL database */
    private static void saveFriends(List<FriendData> friends) throws SQLException {
        DatabaseStore.saveFriends(friends);
    }

    /* Loads friend request data from the MySQL database */
    private static List<FriendRequestData> loadRequests() throws SQLException {
        return DatabaseStore.loadRequests();
    }

    /* Saves friend request data to the MySQL database */
    private static void saveRequests(List<FriendRequestData> requests) throws SQLException {
        DatabaseStore.saveRequests(requests);
    }

    /* Sends a friend request from one user to another, preventing self-requests, duplicates, and requests to existing friends */
    public static void sendRequest(String fromUser, String toUser) throws SQLException {
        if (fromUser.equalsIgnoreCase(toUser)) {
            System.out.println("Cannot send friend request to self: " + fromUser);
            return;
        }

        // Check if both users exist
        if (!UserManager.findUser(fromUser).isPresent()) {
            System.out.println("Sender does not exist: " + fromUser);
            return;
        }
        if (!UserManager.findUser(toUser).isPresent()) {
            System.out.println("Receiver does not exist: " + toUser);
            return;
        }

        if (getFriends(fromUser).stream().anyMatch(f -> f.equalsIgnoreCase(toUser))) {
            System.out.println("Users are already friends: " + fromUser + ", " + toUser);
            return;
        }

        List<FriendRequestData> requests = loadRequests();
        FriendRequestData sender = requests.stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(fromUser))
                .findFirst()
                .orElseGet(() -> {
                    FriendRequestData r = new FriendRequestData(fromUser);
                    requests.add(r);
                    return r;
                });

        FriendRequestData receiver = requests.stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(toUser))
                .findFirst()
                .orElseGet(() -> {
                    FriendRequestData r = new FriendRequestData(toUser);
                    requests.add(r);
                    return r;
                });

        boolean alreadySent = sender.getOutgoing().stream()
                .anyMatch(u -> u.equalsIgnoreCase(toUser));
        if (alreadySent) {
            System.out.println("Friend request already sent from " + fromUser + " to " + toUser);
            return;
        }

        sender.getOutgoing().add(toUser);
        receiver.getIncoming().add(fromUser);
        saveRequests(requests);
        System.out.println("Friend request saved: " + fromUser + " -> " + toUser);
    }

    /* Accepts a friend request, removes it from pending requests, and establishes a mutual friend relationship */
    public static void acceptRequest(String requester, String target) throws SQLException {
        List<FriendRequestData> requests = loadRequests();
        for (FriendRequestData r : requests) {
            if (r.getUsername().equalsIgnoreCase(target)) {
                r.getIncoming().removeIf(u -> u.equalsIgnoreCase(requester));
            }
            if (r.getUsername().equalsIgnoreCase(requester)) {
                r.getOutgoing().removeIf(u -> u.equalsIgnoreCase(target));
            }
        }
        saveRequests(requests);

        List<FriendData> friends = loadFriends();
        addFriend(friends, requester, target);
        addFriend(friends, target, requester);
        saveFriends(friends);
    }

    /* Rejects a pending friend request and removes it from storage */
    public static void rejectRequest(String requester, String target) throws SQLException {
        List<FriendRequestData> requests = loadRequests();
        for (FriendRequestData r : requests) {
            if (r.getUsername().equalsIgnoreCase(target)) {
                r.getIncoming().removeIf(u -> u.equalsIgnoreCase(requester));
            }
            if (r.getUsername().equalsIgnoreCase(requester)) {
                r.getOutgoing().removeIf(u -> u.equalsIgnoreCase(target));
            }
        }
        saveRequests(requests);
    }

    /* Removes a mutual friend relationship between two users */
    public static void removeFriend(String user, String friend) throws SQLException {
        List<FriendData> friends = loadFriends();
        for (FriendData f : friends) {
            if (f.getUsername().equalsIgnoreCase(user)) {
                f.getFriends().removeIf(u -> u.equalsIgnoreCase(friend));
            }
            if (f.getUsername().equalsIgnoreCase(friend)) {
                f.getFriends().removeIf(u -> u.equalsIgnoreCase(user));
            }
        }
        saveFriends(friends);
    }

    /* Adds a friend to a user's friend list if not already present */
    private static void addFriend(List<FriendData> friends, String user, String friend) {
        FriendData data = friends.stream()
                .filter(f -> f.getUsername().equalsIgnoreCase(user))
                .findFirst()
                .orElseGet(() -> {
                    FriendData fd = new FriendData(user);
                    friends.add(fd);
                    return fd;
                });
        if (data.getFriends().stream().noneMatch(u -> u.equalsIgnoreCase(friend))) {
            data.getFriends().add(friend);
        }
    }

    /* Retrieves the list of confirmed friends for a specified user */
    public static List<String> getFriends(String username) throws SQLException {
        return loadFriends().stream()
                .filter(f -> f.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(FriendData::getFriends)
                .orElseGet(ArrayList::new);
    }

    /* Retrieves the list of incoming friend requests for a specified user */
    public static List<String> getIncomingRequests(String username) throws SQLException {
        return loadRequests().stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(FriendRequestData::getIncoming)
                .orElseGet(ArrayList::new);
    }

    /* Retrieves the list of outgoing friend requests sent by a specified user */
    public static List<String> getOutgoingRequests(String username) throws SQLException {
        return loadRequests().stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(FriendRequestData::getOutgoing)
                .orElseGet(ArrayList::new);
    }
}