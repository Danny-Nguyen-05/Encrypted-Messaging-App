package server.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageStore {
    private static final Path UNDELIVERED_FILE = Paths.get("data", "undelivered.json");

    public static List<ChatEntry> loadUndelivered() throws IOException {
        return JsonStore.loadList(UNDELIVERED_FILE, ChatEntry.class);
    }

    public static void saveUndelivered(List<ChatEntry> list) throws IOException {
        JsonStore.saveList(UNDELIVERED_FILE, list);
    }

    public static List<ChatEntry> getUndeliveredMessages(String username, String peerName) throws IOException {
        List<ChatEntry> all = loadUndelivered();
        return all.stream()
                .filter(e -> (e.sender.equals(username) && e.receiver.equals(peerName)) ||
                        (e.sender.equals(peerName) && e.receiver.equals(username)))
                .filter(e -> !e.delivered)
                .collect(Collectors.toList());
    }

    public static void removeDeliveredMessages(String username, String peerName) throws IOException {
        List<ChatEntry> all = loadUndelivered();
        System.out.println("Before removal, total entries: " + all.size());
        int initialSize = all.size();
        boolean removed = all.removeIf(e ->
                 e.sender.equals(peerName)
                        && e.receiver.equals(username)
        );
        System.out.println("After removal, entries remaining: " + all.size() + ", removed: " + (initialSize - all.size()));
        saveUndelivered(all);
        System.out.println("Removal process completed for " + username + " and " + peerName);
    }
}