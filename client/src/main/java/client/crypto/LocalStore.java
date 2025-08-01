package client.crypto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class LocalStore {
    private static final Path STORE_DIR = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "java", "client", "keys"
    );
    private static final Path CHAT_DIR = Paths.get(
            System.getProperty("user.dir"),
            "src", "main", "java", "client", "chats"
    );
    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        try {
            if (!Files.exists(STORE_DIR)) {
                Files.createDirectories(STORE_DIR);
            }
            if (!Files.exists(CHAT_DIR)) {
                Files.createDirectories(CHAT_DIR);
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
        Path path = getChatPath(username, peerName);
        try {
            List<ChatMessageEntry> messages = loadChatMessages(username, peerName);
            messages.add(new ChatMessageEntry(sender, receiver, cipher, timestamp));
            ArrayNode arrayNode = mapper.createArrayNode();
            for (ChatMessageEntry msg : messages) {
                ObjectNode node = mapper.createObjectNode();
                node.put("sender", msg.sender);
                node.put("receiver", msg.receiver);
                node.put("cipher", msg.cipher);
                node.put("timestamp", msg.timestamp);
                arrayNode.add(node);
            }
            Files.createDirectories(path.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), arrayNode);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save chat message for " + username + " to " + peerName, e);
        }
    }

    public static List<ChatMessageEntry> loadChatMessages(String username, String peerName) {
        Path path = getChatPath(username, peerName);
        System.out.println("Attempting to load chat file: " + path.toString());
        try {
            if (!Files.exists(path)) {
                System.out.println("Chat file does not exist, returning empty list");
                return new ArrayList<>();
            }
            List<ChatMessageEntry> messages = mapper.readValue(path.toFile(),
                    mapper.getTypeFactory().constructCollectionType(List.class, ChatMessageEntry.class));
            if (messages.isEmpty()) {
                System.out.println("Chat file is empty");
            } else {
                System.out.println("Successfully read " + messages.size() + " messages");
            }
            messages.sort((m1, m2) -> Long.compare(m1.timestamp, m2.timestamp));
            return messages;
        } catch (IOException e) {
            System.err.println("IOException loading chat messages: " + e.getMessage());
            throw new RuntimeException("Failed to load chat messages for " + username + " to " + peerName, e);
        }
    }

    private static Path getChatPath(String username, String peerName) {
        return CHAT_DIR.resolve(username).resolve(peerName + ".json");
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