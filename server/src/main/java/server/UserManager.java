package server;

import server.storage.*;
import shared.Message;
import shared.MessageType;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserManager {
    private static final int MAX_BAD_TRIES = 5;
    private static final int[] LOCKOUT_MINUTES = {1, 5, 10, 20, 60};
    public static final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();

    private static List<UserData> loadUsers() throws SQLException {
        return DatabaseStore.loadUsers();
    }

    private static void saveUsers(List<UserData> users) throws SQLException {
        DatabaseStore.saveUsers(users);
    }

    public static AuthResult authenticate(String username, String password) {
        try {
            List<UserData> users = loadUsers();
            long now = System.currentTimeMillis();

            for (UserData u : users) {
                if (!u.username.equals(username)) continue;
                if (u.lockoutStage > LOCKOUT_MINUTES.length) {
                    return new AuthResult(false, "Account permanently locked.");
                }

                if (u.lockoutExpiryMs > now) {
                    long secLeft = (u.lockoutExpiryMs - now) / 1000;
                    return new AuthResult(false, "Account locked. Try again in " + secLeft + "s.");
                }

                String hash = PasswordUtil.hash(password, u.salt);
                if (hash.equals(u.passwordHash)) {
                    u.failedAttempts = 0;
                    u.lockoutStage = 0;
                    u.lockoutExpiryMs = 0L;
                    saveUsers(users);
                    return new AuthResult(true, "OK");
                }

                u.failedAttempts++;
                if (u.failedAttempts >= MAX_BAD_TRIES) {
                    u.failedAttempts = 0;
                    u.lockoutStage++;

                    if (u.lockoutStage <= LOCKOUT_MINUTES.length) {
                        int mins = LOCKOUT_MINUTES[u.lockoutStage - 1];
                        u.lockoutExpiryMs = now + mins * 60_000L;
                        saveUsers(users);
                        return new AuthResult(false, "Account locked for " + mins + " minutes.");
                    } else {
                        u.lockoutExpiryMs = Long.MAX_VALUE;
                        saveUsers(users);
                        return new AuthResult(false, "Account permanently locked. Contact support.");
                    }
                }

                int left = MAX_BAD_TRIES - u.failedAttempts;
                saveUsers(users);
                return new AuthResult(false, "Password incorrect (" + left + " tries left).");
            }

            return new AuthResult(false, "Account '" + username + "' does not exist.");
        } catch (SQLException e) {
            e.printStackTrace();
            return new AuthResult(false, "Server error reading users.");
        }
    }

    public static class AuthResult {
        public final boolean success;
        public final String message;
        public AuthResult(boolean s, String m) { success = s; message = m; }
    }

    public static boolean changeUsername(String oldUsername, String newUsername, String oldPassword) {
        try {
            List<UserData> users = loadUsers();
            if (users.stream().anyMatch(u -> u.username.equalsIgnoreCase(newUsername))) {
                return false;
            }
            for (UserData u : users) {
                if (u.username.equals(oldUsername)) {
                    String hash = PasswordUtil.hash(oldPassword, u.salt);
                    if (!hash.equals(u.passwordHash)) return false;
                    u.username = newUsername;
                    saveUsers(users);
                    try {
                        updateFriendsFile(oldUsername, newUsername);
                        updateRequestsFile(oldUsername, newUsername);
                    } catch (SQLException e) {
                        e.printStackTrace();
                        return false;
                    }
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void updateFriendsFile(String oldUser, String newUser) throws SQLException {
        List<FriendData> all = DatabaseStore.loadFriends();
        for (FriendData f : all) {
            if (f.getUsername().equals(oldUser)) {
                f.setUsername(newUser);
            }
            for (int i = 0; i < f.getFriends().size(); i++) {
                if (f.getFriends().get(i).equals(oldUser)) {
                    f.getFriends().set(i, newUser);
                }
            }
        }
        DatabaseStore.saveFriends(all);
    }

    private static void updateRequestsFile(String oldUser, String newUser) throws SQLException {
        List<FriendRequestData> all = DatabaseStore.loadRequests();
        for (FriendRequestData r : all) {
            if (r.getUsername().equals(oldUser)) {
                r.setUsername(newUser);
            }
            for (int i = 0; i < r.getIncoming().size(); i++) {
                if (r.getIncoming().get(i).equals(oldUser)) {
                    r.getIncoming().set(i, newUser);
                }
            }
            for (int i = 0; i < r.getOutgoing().size(); i++) {
                if (r.getOutgoing().get(i).equals(oldUser)) {
                    r.getOutgoing().set(i, newUser);
                }
            }
        }
        DatabaseStore.saveRequests(all);
    }

    public static boolean changePassword(String username, String oldPassword, String newPassword) {
        try {
            List<UserData> users = loadUsers();
            for (UserData u : users) {
                if (u.username.equals(username)) {
                    String existingHash = PasswordUtil.hash(oldPassword, u.salt);
                    if (!existingHash.equals(u.passwordHash)) {
                        return false;
                    }
                    String newSalt = PasswordUtil.generateSalt();
                    u.salt = newSalt;
                    u.passwordHash = PasswordUtil.hash(newPassword, newSalt);
                    saveUsers(users);
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean register(String username, String password) {
        try {
            List<UserData> users = loadUsers();
            boolean exists = users.stream().anyMatch(u -> u.username.equalsIgnoreCase(username));
            if (exists) return false;

            String salt = PasswordUtil.generateSalt();
            String hashedPassword = PasswordUtil.hash(password, salt);
            users.add(new UserData(username, hashedPassword, salt));
            saveUsers(users);
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Optional<UserData> findUser(String username) {
        try {
            List<UserData> users = loadUsers();
            return users.stream().filter(u -> u.username.equalsIgnoreCase(username)).findFirst();
        } catch (SQLException e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public static List<String> getAllUsernames() {
        try {
            List<UserData> users = loadUsers();
            List<String> names = new ArrayList<>();
            for (UserData user : users) {
                names.add(user.username);
            }
            return names;
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static void handleRegister(Message msg, ClientHandler handler) {
        String username = msg.getReceiver();
        String password = msg.getContent();

        if (username.isBlank() || password.isEmpty()) {
            handler.sendMessage(new Message(
                    MessageType.REGISTER_FAILURE, "Server", username,
                    "Username & password required."
            ));
            return;
        }

        boolean success = register(username, password);
        if (!success) {
            handler.sendMessage(new Message(
                    MessageType.REGISTER_FAILURE, "Server", username,
                    "Username '" + username + "' is already taken."
            ));
        } else {
            handler.sendMessage(new Message(
                    MessageType.REGISTER_SUCCESS, "Server", username,
                    "OK"
            ));
        }
    }

    public static void handleLogin(Message msg, ClientHandler handler) throws SQLException {
        String username = msg.getReceiver();
        String password = msg.getContent();

        AuthResult result = authenticate(username, password);
        MessageType replyType = result.success
                ? MessageType.LOGIN_SUCCESS
                : MessageType.LOGIN_FAILURE;

        if (result.success) {
            UserManager.activeClients.put(username, handler);
            handler.setUsername(username);
        }
        handler.sendMessage(new Message(
                replyType,
                "Server",
                username,
                result.message
        ));
    }

    public static void handleUpdatePublicKey(Message msg, ClientHandler handler) throws SQLException {
        String user = msg.getReceiver();
        String pubB64 = msg.getContent();

        System.out.println("=== UPDATE PUBLIC KEY DEBUG ===");
        System.out.println("User: " + user);
        System.out.println("Public key length: " + (pubB64 != null ? pubB64.length() : "null"));

        if (pubB64 != null && !pubB64.isEmpty()) {
            String cleanedKey = cleanBase64String(pubB64);
            System.out.println("Cleaned key length: " + cleanedKey.length());
            System.out.println("Original key first 50 chars: " + pubB64.substring(0, Math.min(50, pubB64.length())));
            System.out.println("Cleaned key first 50 chars: " + cleanedKey.substring(0, Math.min(50, cleanedKey.length())));

            try {
                java.util.Base64.getDecoder().decode(cleanedKey);
                System.out.println("Cleaned key is valid Base64");
            } catch (IllegalArgumentException e) {
                System.out.println("ERROR: Cleaned key is still invalid Base64: " + e.getMessage());
                return;
            }

            List<UserData> users = loadUsers();
            for (UserData u : users) {
                if (u.username.equalsIgnoreCase(user)) {
                    u.publicKeyBase64 = cleanedKey;
                    saveUsers(users);
                    System.out.println("Public key updated successfully for user: " + user);
                    break;
                }
            }
        }
        System.out.println("===============================");
    }

    public static void handleRequestPublicKey(Message msg, ClientHandler handler) throws SQLException {
        String requestingUser = msg.getSender();
        String targetUser = msg.getReceiver();

        System.out.println("=== PUBLIC KEY REQUEST DEBUG ===");
        System.out.println("Request from: " + requestingUser);
        System.out.println("Target user: " + targetUser);

        if (requestingUser == null || requestingUser.isEmpty()) {
            System.out.println("ERROR: Requesting user is null or empty");
            requestingUser = handler.getUsername();
            System.out.println("Using handler username: " + requestingUser);
        }

        Optional<UserData> opt = findUser(targetUser);
        if (opt.isPresent()) {
            UserData userData = opt.get();
            String publicKey = userData.publicKeyBase64;

            System.out.println("Found user: " + userData.username);
            System.out.println("Public key exists: " + (publicKey != null && !publicKey.isEmpty()));

            if (publicKey != null && !publicKey.isEmpty()) {
                System.out.println("Public key length: " + publicKey.length());
                System.out.println("Public key first 50 chars: " + publicKey.substring(0, Math.min(50, publicKey.length())));
                System.out.println("Public key last 50 chars: " + publicKey.substring(Math.max(0, publicKey.length() - 50)));

                if (publicKey.contains("_") || publicKey.contains("-")) {
                    System.out.println("WARNING: Public key contains URL-safe Base64 characters");
                }

                try {
                    java.util.Base64.getDecoder().decode(publicKey);
                    System.out.println("Public key is valid standard Base64");
                } catch (IllegalArgumentException e) {
                    System.out.println("Public key is NOT valid standard Base64: " + e.getMessage());
                    try {
                        java.util.Base64.getUrlDecoder().decode(publicKey);
                        System.out.println("Public key is valid URL-safe Base64");
                    } catch (IllegalArgumentException e2) {
                        System.out.println("Public key is NOT valid URL-safe Base64: " + e2.getMessage());
                    }
                }

                String sanitized = cleanBase64String(publicKey);

                handler.sendMessage(new Message(
                        MessageType.PUBLIC_KEY_RESPONSE,
                        "Server",
                        requestingUser,
                        sanitized
                ));
                System.out.println("Public key sent successfully");
            } else {
                handler.sendMessage(new Message(
                        MessageType.PUBLIC_KEY_RESPONSE,
                        "Server",
                        requestingUser,
                        "NO_KEY"
                ));
                System.out.println("User found but no public key stored");
            }
        } else {
            System.out.println("User not found: " + targetUser);
            handler.sendMessage(new Message(
                    MessageType.PUBLIC_KEY_RESPONSE,
                    "Server",
                    requestingUser,
                    "USER_NOT_FOUND"
            ));
        }
        System.out.println("===============================");
    }

    private static String cleanBase64String(String base64String) {
        if (base64String == null || base64String.isEmpty()) {
            return base64String;
        }

        base64String = base64String
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "");

        String cleaned = base64String.replaceAll("\\s+", "");
        cleaned = cleaned.replace('-', '+').replace('_', '/');
        int padding = 4 - (cleaned.length() % 4);
        if (padding != 4) {
            cleaned += "=".repeat(padding);
        }

        return cleaned;
    }

    public static void handleHistoryRequest(Message msg, ClientHandler handler) throws SQLException {
        String me = msg.getSender();
        String peerName = msg.getReceiver();

        System.out.println("Handling history request: from=" + me + ", for=" + peerName);
        List<ChatEntry> messages = DatabaseStore.getUndeliveredMessages(me, peerName);
        if (messages.isEmpty()) {
            System.out.println("No undelivered messages found for " + me + " and " + peerName);
        } else {
            System.out.println("Found " + messages.size() + " undelivered messages");
            for (ChatEntry e : messages) {
                if (Objects.equals(e.receiver, me)) {
                    handler.sendMessage(new Message(
                            MessageType.HISTORY_RESPONSE,
                            e.sender,
                            me,
                            e.cipher
                    ));

                    e.delivered = true;
                    System.out.println("Sent undelivered message from " + e.sender + " to " + me);
                }
            }
            DatabaseStore.removeDeliveredMessages(me, peerName);
            System.out.println("Removed delivered messages from database");
        }
    }

    public static void handleChatMessage(Message msg, ClientHandler handler) throws SQLException {
        String from = msg.getSender();
        String toUser = msg.getReceiver();
        String[] parts = msg.getContent().split("\\|", 2);
        String cipher = parts[0];
        String chatState = parts.length > 1 ? parts[1] : "NOT_IN_CHAT";
        long now = System.currentTimeMillis();

        ClientHandler recipient = ServerMain.onlineUsers.get(toUser);
        String activePartner = ClientHandler.activeChatPartner.get(toUser);
        if (recipient != null && from.equals(activePartner)) {
            try {
                recipient.sendMessage(new Message(
                        MessageType.CHAT_MESSAGE,
                        from,
                        toUser,
                        cipher
                ));
                System.out.println("Message forwarded directly to " + toUser + " who is in chat");
            } catch (Exception e) {
                System.err.println("Failed to forward message to " + toUser + ": " + e.getMessage());
                ServerMain.onlineUsers.remove(toUser);
                List<ChatEntry> undelivered = DatabaseStore.loadUndelivered();
                undelivered.add(new ChatEntry(from, toUser, cipher, now, false));
                DatabaseStore.saveUndelivered(undelivered);
                System.out.println("Message stored in database due to forwarding failure, count: " + undelivered.size());
            }
        } else {
            System.out.println("User " + toUser + " is not in chat or offline, storing in database");
            List<ChatEntry> undelivered = DatabaseStore.loadUndelivered();
            undelivered.add(new ChatEntry(from, toUser, cipher, now, false));
            DatabaseStore.saveUndelivered(undelivered);
            System.out.println("Undelivered message saved, count: " + undelivered.size());
        }
    }
}