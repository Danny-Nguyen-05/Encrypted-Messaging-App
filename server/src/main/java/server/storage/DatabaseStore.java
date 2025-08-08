package server.storage;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;
import java.util.Properties;

public class DatabaseStore {
    private static final String DB_URL;
    private static final String DB_USER;
    private static final String DB_PASSWORD;

    static {
        Properties props = new Properties();
        try (InputStream input = DatabaseStore.class.getClassLoader().getResourceAsStream("server/storage/application.properties")) {
            if (input == null) {
                throw new IllegalStateException("application.properties not found in server/storage/");
            }
            props.load(input);
            DB_URL = props.getProperty("db.url");
            DB_USER = props.getProperty("db.user");
            DB_PASSWORD = props.getProperty("db.password");
            System.out.println("Loaded DB_URL: " + DB_URL); // Debug output
        } catch (IOException e) {
            throw new RuntimeException("Failed to load database properties from server/storage/application.properties", e);
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public static List<UserData> loadUsers() throws SQLException {
        List<UserData> users = new ArrayList<>();
        String sql = "SELECT username, password_hash, salt, public_key_base64, failed_attempts, lockout_stage, lockout_expiry_ms FROM users";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                UserData user = new UserData();
                user.username = rs.getString("username");
                user.passwordHash = rs.getString("password_hash");
                user.salt = rs.getString("salt");
                user.publicKeyBase64 = rs.getString("public_key_base64");
                user.failedAttempts = rs.getInt("failed_attempts");
                user.lockoutStage = rs.getInt("lockout_stage");
                user.lockoutExpiryMs = rs.getLong("lockout_expiry_ms");
                users.add(user);
            }
        }
        return users;
    }

    public static void saveUsers(List<UserData> users) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, salt, public_key_base64, failed_attempts, lockout_stage, lockout_expiry_ms) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                "password_hash = VALUES(password_hash), salt = VALUES(salt), public_key_base64 = VALUES(public_key_base64), " +
                "failed_attempts = VALUES(failed_attempts), lockout_stage = VALUES(lockout_stage), lockout_expiry_ms = VALUES(lockout_expiry_ms)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (UserData user : users) {
                stmt.setString(1, user.username);
                stmt.setString(2, user.passwordHash);
                stmt.setString(3, user.salt);
                stmt.setString(4, user.publicKeyBase64);
                stmt.setInt(5, user.failedAttempts);
                stmt.setInt(6, user.lockoutStage);
                stmt.setLong(7, user.lockoutExpiryMs);
                stmt.executeUpdate();
            }
        }
    }

    public static List<FriendData> loadFriends() throws SQLException {
        List<FriendData> friends = new ArrayList<>();
        String sql = "SELECT u.username, GROUP_CONCAT(f.friend_id) AS friend_ids " +
                "FROM users u LEFT JOIN friends f ON u.user_id = f.user_id " +
                "GROUP BY u.user_id, u.username";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                FriendData friendData = new FriendData(rs.getString("username"));
                String friendIds = rs.getString("friend_ids");
                if (friendIds != null) {
                    String sqlFriends = "SELECT username FROM users WHERE user_id IN (" + friendIds + ")";
                    try (PreparedStatement stmtFriends = conn.prepareStatement(sqlFriends); ResultSet rsFriends = stmtFriends.executeQuery()) {
                        while (rsFriends.next()) {
                            friendData.getFriends().add(rsFriends.getString("username"));
                        }
                    }
                }
                friends.add(friendData);
            }
        }
        return friends;
    }

    public static void saveFriends(List<FriendData> friends) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String deleteSql = "DELETE FROM friends WHERE user_id = (SELECT user_id FROM users WHERE username = ?)";
            String insertSql = "INSERT INTO friends (user_id, friend_id) VALUES ((SELECT user_id FROM users WHERE username = ?), (SELECT user_id FROM users WHERE username = ?))";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql); PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (FriendData friendData : friends) {
                    deleteStmt.setString(1, friendData.getUsername());
                    deleteStmt.executeUpdate();
                    for (String friend : friendData.getFriends()) {
                        insertStmt.setString(1, friendData.getUsername());
                        insertStmt.setString(2, friend);
                        insertStmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static List<FriendRequestData> loadRequests() throws SQLException {
        List<FriendRequestData> requests = new ArrayList<>();
        String sql = "SELECT u.username, GROUP_CONCAT(fr.sender_id) AS incoming_ids, GROUP_CONCAT(fr2.receiver_id) AS outgoing_ids " +
                "FROM users u " +
                "LEFT JOIN friend_requests fr ON u.user_id = fr.receiver_id AND fr.status = 'PENDING' " +
                "LEFT JOIN friend_requests fr2 ON u.user_id = fr2.sender_id AND fr2.status = 'PENDING' " +
                "GROUP BY u.user_id, u.username";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                FriendRequestData requestData = new FriendRequestData(rs.getString("username"));
                String incomingIds = rs.getString("incoming_ids");
                String outgoingIds = rs.getString("outgoing_ids");
                if (incomingIds != null) {
                    String sqlIncoming = "SELECT username FROM users WHERE user_id IN (" + incomingIds + ")";
                    try (PreparedStatement stmtIncoming = conn.prepareStatement(sqlIncoming); ResultSet rsIncoming = stmtIncoming.executeQuery()) {
                        while (rsIncoming.next()) {
                            requestData.getIncoming().add(rsIncoming.getString("username"));
                        }
                    }
                }
                if (outgoingIds != null) {
                    String sqlOutgoing = "SELECT username FROM users WHERE user_id IN (" + outgoingIds + ")";
                    try (PreparedStatement stmtOutgoing = conn.prepareStatement(sqlOutgoing); ResultSet rsOutgoing = stmtOutgoing.executeQuery()) {
                        while (rsOutgoing.next()) {
                            requestData.getOutgoing().add(rsOutgoing.getString("username"));
                        }
                    }
                }
                requests.add(requestData);
            }
        }
        return requests;
    }

    public static void saveRequests(List<FriendRequestData> requests) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String deleteSql = "DELETE FROM friend_requests WHERE sender_id = (SELECT user_id FROM users WHERE username = ?)";
            String insertSql = "INSERT INTO friend_requests (sender_id, receiver_id, status) VALUES ((SELECT user_id FROM users WHERE username = ?), (SELECT user_id FROM users WHERE username = ?), 'PENDING')";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql); PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (FriendRequestData requestData : requests) {
                    // Delete only outgoing requests for this user
                    deleteStmt.setString(1, requestData.getUsername());
                    int rowsDeleted = deleteStmt.executeUpdate();
                    System.out.println("Deleted " + rowsDeleted + " outgoing friend requests for user: " + requestData.getUsername());

                    // Insert outgoing requests
                    for (String outgoing : requestData.getOutgoing()) {
                        try {
                            insertStmt.setString(1, requestData.getUsername());
                            insertStmt.setString(2, outgoing);
                            int rowsInserted = insertStmt.executeUpdate();
                            System.out.println("Inserted friend request: " + requestData.getUsername() + " -> " + outgoing);
                        } catch (SQLException e) {
                            System.out.println("Failed to insert friend request: " + requestData.getUsername() + " -> " + outgoing + ". Error: " + e.getMessage());
                            // Continue to avoid failing the entire transaction
                        }
                    }
                }
                conn.commit();
                System.out.println("Friend requests committed successfully.");
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Rolling back friend requests update due to error: " + e.getMessage());
                throw e;
            }
        }
    }

    public static List<ChatEntry> loadUndelivered() throws SQLException {
        List<ChatEntry> messages = new ArrayList<>();
        String sql = "SELECT u1.username AS sender, u2.username AS receiver, m.cipher, m.timestamp, m.delivered " +
                "FROM pending_messages m " +
                "JOIN users u1 ON m.sender_id = u1.user_id " +
                "JOIN users u2 ON m.receiver_id = u2.user_id";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                messages.add(new ChatEntry(
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("cipher"),
                        rs.getLong("timestamp"),
                        rs.getBoolean("delivered")
                ));
            }
        }
        return messages;
    }

    public static void saveUndelivered(List<ChatEntry> messages) throws SQLException {
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String deleteSql = "DELETE FROM pending_messages";
            String insertSql = "INSERT INTO pending_messages (sender_id, receiver_id, cipher, timestamp, delivered) " +
                    "VALUES ((SELECT user_id FROM users WHERE username = ?), (SELECT user_id FROM users WHERE username = ?), ?, ?, ?)";
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql); PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                deleteStmt.executeUpdate();
                for (ChatEntry msg : messages) {
                    insertStmt.setString(1, msg.sender);
                    insertStmt.setString(2, msg.receiver);
                    insertStmt.setString(3, msg.cipher);
                    insertStmt.setLong(4, msg.timestamp);
                    insertStmt.setBoolean(5, msg.delivered);
                    insertStmt.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public static List<ChatEntry> getUndeliveredMessages(String username, String peerName) throws SQLException {
        List<ChatEntry> messages = new ArrayList<>();
        String sql = "SELECT u1.username AS sender, u2.username AS receiver, m.cipher, m.timestamp, m.delivered " +
                "FROM pending_messages m " +
                "JOIN users u1 ON m.sender_id = u1.user_id " +
                "JOIN users u2 ON m.receiver_id = u2.user_id " +
                "WHERE (u1.username = ? AND u2.username = ? OR u1.username = ? AND u2.username = ?) AND m.delivered = FALSE";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, peerName);
            stmt.setString(3, peerName);
            stmt.setString(4, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(new ChatEntry(
                            rs.getString("sender"),
                            rs.getString("receiver"),
                            rs.getString("cipher"),
                            rs.getLong("timestamp"),
                            rs.getBoolean("delivered")
                    ));
                }
            }
        }
        return messages;
    }

    public static void removeDeliveredMessages(String username, String peerName) throws SQLException {
        String sql = "DELETE FROM pending_messages WHERE sender_id = (SELECT user_id FROM users WHERE username = ?) AND receiver_id = (SELECT user_id FROM users WHERE username = ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, peerName);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }
}