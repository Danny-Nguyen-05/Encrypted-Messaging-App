package server;

import shared.Message;
import shared.MessageType;
import server.storage.UserData;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Collections;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private String username;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    static final ConcurrentHashMap<String, String> activeChatPartner = new ConcurrentHashMap<>();

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    /* Sends a message to the client over the socket */
    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Main loop to handle incoming client messages */
    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            while (true) {
                Message message = (Message) in.readObject();
                handleMessage(message);
            }
        } catch (Exception e) {
            // Handle disconnection
        } finally {
            if (username != null) {
                ServerMain.onlineUsers.remove(username);
                activeChatPartner.remove(username);
                GUIServer.log(username + " has disconnected from the server.");
            }
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    /* Closes the client socket connection */
    public void shutdown() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Processes different types of incoming messages from the client */
    private void handleMessage(Message message) throws IOException {
        switch (message.getType()) {
            case REGISTER -> UserManager.handleRegister(message, this);
            case LOGIN -> UserManager.handleLogin(message, this);
            case LOGOUT -> handleLogout();
            case CHANGE_PASSWORD -> handleChangePassword(message);
            case CHAT -> handleChat(message);
            case UPDATE_PUBLIC_KEY -> UserManager.handleUpdatePublicKey(message, this);
            case REQUEST_PUBLIC_KEY -> UserManager.handleRequestPublicKey(message, this);
            case CHAT_MESSAGE -> UserManager.handleChatMessage(message, this);
            case HISTORY_REQUEST -> UserManager.handleHistoryRequest(message, this);
            case SEARCH_USER -> handleSearchUser(message);
            case SEND_FRIEND_REQUEST -> handleFriendRequest(message);
            case VIEW_PENDING_REQUESTS -> handleViewPendingRequests(message);
            case ACCEPT_FRIEND_REQUEST -> handleFriendAccept(message);
            case LIST_FRIENDS -> handleFriendList(message);
            case REJECT_FRIEND_REQUEST -> handleRejectRequest(message);
            case REMOVE_FRIEND -> handleRemoveFriend(message);
            case CHAT_STATE_UPDATE -> {
                String state = message.getContent();
                String user = message.getSender();
                String partner = message.getReceiver();
                if (state.equals("IN_CHAT")) {
                    activeChatPartner.put(user, partner);
                } else {
                    activeChatPartner.remove(user);
                }
                System.out.println(user + " is " +
                        (state.equals("IN_CHAT")? "now chatting with " + partner
                                : "no longer in a chat"));
            }
            default -> sendMessage(new Message(
                    MessageType.CHAT, "Server", username, "Unknown request."));
        }
    }

    /* Handles client logout and closes the connection */
    private void handleLogout() {
        sendMessage(new Message(
                MessageType.LOGOUT_SUCCESS,
                "Server",
                username,
                "OK"
        ));
        if (username != null) {
            ServerMain.onlineUsers.remove(username);
            activeChatPartner.remove(username);
            GUIServer.log(username + " has disconnected from the server.");
        }
        try { socket.close(); } catch (IOException ignored) {}
    }

    /* Processes password change requests */
    private void handleChangePassword(Message msg) {
        String[] parts = msg.getContent().split(":", 2);
        String oldP = parts[0], newP = parts.length>1 ? parts[1] : "";
        boolean ok = UserManager.changePassword(username, oldP, newP);
        if (ok) {
            sendMessage(new Message(
                    MessageType.CHANGE_PASSWORD_SUCCESS,
                    "Server", username, "Password updated."
            ));
        } else {
            sendMessage(new Message(
                    MessageType.CHANGE_PASSWORD_FAILURE,
                    "Server", username, "Could not change password."
            ));
        }
    }

    /* Handles user search requests and returns matching usernames */
    private void handleSearchUser(Message message) {
        String searchTerm = message.getContent().toLowerCase();
        String me         = this.username.toLowerCase();
        List<String> all = UserManager.getAllUsernames();
        List<String> matches = all.stream()
                .filter(name -> name.toLowerCase().contains(searchTerm))
                .filter(name -> !name.equalsIgnoreCase(me))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            sendMessage(new Message(
                    MessageType.USER_NOT_FOUND,
                    "Server",
                    username,
                    message.getContent()
            ));
        } else {
            String payload = String.join(",", matches);
            sendMessage(new Message(
                    MessageType.USER_FOUND,
                    "Server",
                    username,
                    payload
            ));
        }
    }

    /* Forwards chat messages to the recipient if online */
    private void handleChat(Message message) {
        String recipient = message.getReceiver();
        ClientHandler target = ServerMain.onlineUsers.get(recipient);
        if (target != null) {
            target.sendMessage(message);
        } else {
            sendMessage(new Message(MessageType.CHAT, "Server", username, recipient + " is offline."));
        }
    }

    /* Processes friend request submissions */
    private void handleFriendRequest(Message message) {
        String target = message.getContent();
        try {
            FriendManager.sendRequest(username, target);
            System.out.println("SERVER → Persisted request: from=" + username
                    + ", to=" + target);
            sendMessage(new Message(
                    MessageType.FRIEND_REQUEST_SENT,
                    "Server", username,
                    "Request sent to " + target
            ));
            ClientHandler targetHandler = ServerMain.onlineUsers.get(target);
            if (targetHandler != null) {
                List<String> incoming = FriendManager.getIncomingRequests(target);
                List<String> outgoing = FriendManager.getOutgoingRequests(target);
                String payload = "INCOMING:" + String.join(",", incoming)
                        + ";OUTGOING:" + String.join(",", outgoing);
                targetHandler.sendMessage(new Message(
                        MessageType.PENDING_REQUESTS_LIST,
                        "Server", target,
                        payload
                ));
            }
        } catch (IOException e) {
            sendMessage(new Message(
                    MessageType.FRIEND_REQUEST_FAILED,
                    "Server", username,
                    "Error sending request"
            ));
        }
    }

    /* Retrieves and sends the list of pending friend requests */
    private void handleViewPendingRequests(Message message) {
        try {
            List<String> incoming = FriendManager.getIncomingRequests(username);
            List<String> outgoing = FriendManager.getOutgoingRequests(username);
            String content = "INCOMING:" + String.join(",", incoming)
                    + ";OUTGOING:" + String.join(",", outgoing);
            sendMessage(new Message(MessageType.PENDING_REQUESTS_LIST, "Server", username, content));
        } catch (IOException e) {
            sendMessage(new Message(MessageType.PENDING_REQUESTS_LIST, "Server", username, "Error retrieving pending requests"));
        }
    }

    /* Processes acceptance of a friend request */
    private void handleFriendAccept(Message message) {
        try {
            String requester = message.getContent();
            FriendManager.acceptRequest(requester, username);
            sendMessage(new Message(
                    MessageType.FRIEND_ADDED,
                    "Server",
                    username,
                    requester
            ));
            ClientHandler requesterHandler = ServerMain.onlineUsers.get(requester);
            if (requesterHandler != null) {
                requesterHandler.sendMessage(new Message(MessageType.FRIEND_ADDED, "Server", requester, username));
            }
        } catch (IOException e) {
            sendMessage(new Message(MessageType.FRIEND_ADD_FAILED, "Server", username, "Error accepting request"));
        }
    }

    /* Sends the list of friends for the user */
    private void handleFriendList(Message message) {
        try {
            List<String> list = FriendManager.getFriends(username);
            list.sort(String.CASE_INSENSITIVE_ORDER);
            sendMessage(new Message(MessageType.FRIENDS_LIST, "Server", username, String.join(",", list)));
        } catch (IOException e) {
            sendMessage(new Message(MessageType.FRIENDS_LIST, "Server", username, "Error retrieving friend list"));
        }
    }

    /* Processes rejection of a friend request */
    private void handleRejectRequest(Message message) {
        try {
            String fromUser = message.getContent();
            FriendManager.rejectRequest(fromUser, username);
            sendMessage(new Message(MessageType.FRIEND_REQUEST_REJECTED,
                    "Server", username,
                    "Rejected friend request from " + fromUser));
            ClientHandler requester = ServerMain.onlineUsers.get(fromUser);
            if (requester != null) {
                requester.sendMessage(new Message(MessageType.FRIEND_REQUEST_REJECTED,
                        "Server", fromUser,
                        username + " rejected your friend request"));
            }
        } catch (IOException e) {
            sendMessage(new Message(MessageType.FRIEND_REQUEST_REJECT_FAILED,
                    "Server", username,
                    "Error rejecting request"));
        }
    }

    /* Removes a friend from the user's friend list */
    private void handleRemoveFriend(Message message) {
        try {
            String friend = message.getContent();
            FriendManager.removeFriend(username, friend);
            sendMessage(new Message(MessageType.FRIEND_REMOVED,
                    "Server", username,
                    "Removed friend: " + friend));
            ClientHandler other = ServerMain.onlineUsers.get(friend);
            if (other != null) {
                other.sendMessage(new Message(MessageType.FRIEND_REMOVED,
                        "Server", friend,
                        username + " has removed you"));
            }
        } catch (IOException e) {
            sendMessage(new Message(MessageType.FRIEND_REMOVE_FAILED,
                    "Server", username,
                    "Error removing friend"));
        }
    }

    /* Sets the username for this client handler and logs connection */
    public void setUsername(String username) {
        this.username = username;
        ServerMain.onlineUsers.put(username, this);
        GUIServer.log(username + " has connected to the server.");
    }

    /* Returns the username associated with this client handler */
    public String getUsername() {
        return username;
    }
}