package server.storage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MessageStore {
    public static List<ChatEntry> loadUndelivered() throws SQLException {
        return DatabaseStore.loadUndelivered();
    }

    public static void saveUndelivered(List<ChatEntry> list) throws SQLException {
        DatabaseStore.saveUndelivered(list);
    }

    public static List<ChatEntry> getUndeliveredMessages(String username, String peerName) throws SQLException {
        return DatabaseStore.getUndeliveredMessages(username, peerName);
    }

    public static void removeDeliveredMessages(String username, String peerName) throws SQLException {
        DatabaseStore.removeDeliveredMessages(username, peerName);
    }
}