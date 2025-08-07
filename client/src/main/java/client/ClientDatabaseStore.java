package client;

import client.crypto.LocalStore.ChatMessageEntry;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClientDatabaseStore {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/client_messaging_app?useSSL=false";
    private static final String DB_USER = "root"; // Replace with your MySQL username
    private static final String DB_PASSWORD = "cuongphuc48"; // Replace with your MySQL password

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Saves a chat message to the client's database.
     */
    public static void saveChatMessage(String username, String peerName, String sender, String receiver, String cipher, long timestamp) throws SQLException {
        String insertSql = "INSERT INTO chat_messages (user_id, peer_id, sender_id, receiver_id, cipher, timestamp) " +
                "VALUES ((SELECT user_id FROM users WHERE username = ?), " +
                "(SELECT user_id FROM users WHERE username = ?), " +
                "(SELECT user_id FROM users WHERE username = ?), " +
                "(SELECT user_id FROM users WHERE username = ?), ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            stmt.setString(1, username);
            stmt.setString(2, peerName);
            stmt.setString(3, sender);
            stmt.setString(4, receiver);
            stmt.setString(5, cipher);
            stmt.setLong(6, timestamp);
            int rows = stmt.executeUpdate();
            System.out.println("Saved chat message for " + username + " to " + peerName + ": " + rows + " rows affected");
        } catch (SQLException e) {
            System.err.println("Failed to save chat message for " + username + " to " + peerName + ": " + e.getMessage());
            throw e;
        }
    }

    /**
     * Loads chat messages for a user and their peer from the database.
     */
    public static List<ChatMessageEntry> loadChatMessages(String username, String peerName) throws SQLException {
        List<ChatMessageEntry> messages = new ArrayList<>();
        String sql = "SELECT u1.username AS sender, u2.username AS receiver, cm.cipher, cm.timestamp " +
                "FROM chat_messages cm " +
                "JOIN users u1 ON cm.sender_id = u1.user_id " +
                "JOIN users u2 ON cm.receiver_id = u2.user_id " +
                "WHERE cm.user_id = (SELECT user_id FROM users WHERE username = ?) " +
                "AND cm.peer_id = (SELECT user_id FROM users WHERE username = ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, peerName);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(new ChatMessageEntry(
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("cipher"),
                            rs.getLong("timestamp")
                    ));
                }
            }
            System.out.println("Loaded " + messages.size() + " chat messages for " + username + " with " + peerName);
        } catch (SQLException e) {
            System.err.println("Failed to load chat messages for " + username + " with " + peerName + ": " + e.getMessage());
            throw e;
        }
        messages.sort((m1, m2) -> Long.compare(m1.timestamp, m2.timestamp));
        return messages;
    }

    /**
     * Ensures a user exists in the users table, creating it if necessary.
     */
    public static void ensureUserExists(String username) throws SQLException {
        String sql = "INSERT IGNORE INTO users (username) VALUES (?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }
}