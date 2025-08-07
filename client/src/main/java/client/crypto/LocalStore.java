package client.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import client.ClientDatabaseStore;

public class LocalStore {
    private static final Path STORE_DIR = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "java", "client", "keys"
    );
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        try {
            if (!Files.exists(STORE_DIR)) {
                Files.createDirectories(STORE_DIR);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directories", e);
        }
    }

    public static boolean privateKeyExists(String username) {
        return Files.exists(getPath(username, "privateKey"));
    }

    public static boolean exists(String username, String keyName) {
        return Files.exists(getPath(username, keyName));
    }

    public static void save(String username, String keyName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot save empty key: " + keyName);
        }
        Path path = getPath(username, keyName);
        try {
            Files.write(path, value.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save key: " + keyName + " for user: " + username, e);
        }
    }

    public static String load(String username, String keyName) {
        Path path = getPath(username, keyName);
        try {
            if (!Files.exists(path)) {
                throw new RuntimeException("Key file does not exist: " + keyName + " for user: " + username);
            }
            String content = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new RuntimeException("Key file is empty: " + keyName + " for user: " + username);
            }
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load key: " + keyName + " for user: " + username, e);
        }
    }

    private static Path getPath(String username, String keyName) {
        return STORE_DIR.resolve(username + "_" + keyName + ".txt");
    }

    public static void saveChatMessage(String username, String peerName, String sender, String receiver, String cipher, long timestamp) {
        try {
            ClientDatabaseStore.ensureUserExists(username);
            ClientDatabaseStore.ensureUserExists(peerName);
            ClientDatabaseStore.ensureUserExists(sender);
            ClientDatabaseStore.ensureUserExists(receiver);
            ClientDatabaseStore.saveChatMessage(username, peerName, sender, receiver, cipher, timestamp);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save chat message for " + username + " to " + peerName, e);
        }
    }

    public static List<ChatMessageEntry> loadChatMessages(String username, String peerName) {
        try {
            ClientDatabaseStore.ensureUserExists(username);
            ClientDatabaseStore.ensureUserExists(peerName);
            return ClientDatabaseStore.loadChatMessages(username, peerName);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load chat messages for " + username + " to " + peerName, e);
        }
    }

    public static class ChatMessageEntry {
        public String sender;
        public String receiver;
        public String cipher;
        public long timestamp;

        public ChatMessageEntry() {}

        public ChatMessageEntry(String sender, String receiver, String cipher, long timestamp) {
            this.sender = sender;
            this.receiver = receiver;
            this.cipher = cipher;
            this.timestamp = timestamp;
        }
    }
}