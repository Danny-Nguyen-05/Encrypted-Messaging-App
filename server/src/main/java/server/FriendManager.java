package server;

import server.storage.FriendData;
import server.storage.FriendRequestData;
import server.storage.JsonStore;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/* Manages friend relationships and friend request operations, storing data in JSON files with case-insensitive username handling */
public class FriendManager {
    private static final Path FRIEND_FILE = Paths.get("data/friends.json");
    private static final Path REQUEST_FILE = Paths.get("data/requests.json");

    /* Loads friend relationships from the JSON storage file */
    private static List<FriendData> loadFriends() throws IOException {
        return JsonStore.loadList(FRIEND_FILE, FriendData.class);
    }

    /* Saves friend relationships to the JSON storage file */
    private static void saveFriends(List<FriendData> friends) throws IOException {
        JsonStore.saveList(FRIEND_FILE, friends);
    }

    /* Loads friend request data from the JSON storage file */
    private static List<FriendRequestData> loadRequests() throws IOException {
        return JsonStore.loadList(REQUEST_FILE, FriendRequestData.class);
    }

    /* Saves friend request data to the JSON storage file */
    private static void saveRequests(List<FriendRequestData> requests) throws IOException {
        JsonStore.saveList(REQUEST_FILE, requests);
    }

    /* Sends a friend request from one user to another, preventing self-requests, duplicates, and requests to existing friends */
    public static void sendRequest(String fromUser, String toUser) throws IOException {
        if (fromUser.equalsIgnoreCase(toUser)) {
            return;
        }

        if (getFriends(fromUser).stream().anyMatch(f -> f.equalsIgnoreCase(toUser))) {
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
            return;
        }

        sender.getOutgoing().add(toUser);
        receiver.getIncoming().add(fromUser);
        saveRequests(requests);
    }

    /* Accepts a friend request, removes it from pending requests, and establishes a mutual friend relationship */
    public static void acceptRequest(String requester, String target) throws IOException {
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
    public static void rejectRequest(String requester, String target) throws IOException {
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
    public static void removeFriend(String user, String friend) throws IOException {
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
    public static List<String> getFriends(String username) throws IOException {
        return loadFriends().stream()
                .filter(f -> f.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(FriendData::getFriends)
                .orElseGet(ArrayList::new);
    }

    /* Retrieves the list of incoming friend requests for a specified user */
    public static List<String> getIncomingRequests(String username) throws IOException {
        return loadRequests().stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(FriendRequestData::getIncoming)
                .orElseGet(ArrayList::new);
    }

    /* Retrieves the list of outgoing friend requests sent by a specified user */
    public static List<String> getOutgoingRequests(String username) throws IOException {
        return loadRequests().stream()
                .filter(r -> r.getUsername().equalsIgnoreCase(username))
                .findFirst()
                .map(FriendRequestData::getOutgoing)
                .orElseGet(ArrayList::new);
    }
}