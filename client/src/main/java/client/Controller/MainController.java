package client.Controller;

import client.ChatClient;
import client.crypto.CryptoUtil;
import client.crypto.LocalStore;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import shared.Message;
import shared.MessageType;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;

public class MainController {
    @FXML private TabPane tabPane;
    @FXML private ListView<String> friendList;
    @FXML private ListView<String> chatList;
    @FXML private ListView<String> requestList;
    @FXML private ListView<String> exploreList;
    @FXML private Label greetingLabel;
    @FXML private TextField exploreField;
    @FXML private TextField chatInput;
    @FXML private Button sendButton;
    @FXML private Button settingsButton;

    private ChatClient chatClient;
    private String username;
    private final Map<String, PublicKey> friendPubKeyMap = new HashMap<>();
    private PrivateKey myPrivateKey;
    private PublicKey myPublicKey;

    /* Initializes the main UI, sets up encryption keys, and loads initial data */
    public void init(ChatClient chatClient, String username) {
        this.chatClient = chatClient;
        this.username = username;
        chatClient.setOnMessage(this::onMessageReceived);

        try {
            boolean havePriv = LocalStore.privateKeyExists(username);
            boolean havePub = LocalStore.exists(username, "publicKey");
            String pubB64;

            if (havePriv && havePub) {
                try {
                    myPrivateKey = CryptoUtil.decodePrivateKey(LocalStore.load(username, "privateKey"));
                    myPublicKey = CryptoUtil.decodePublicKey(LocalStore.load(username, "publicKey"));
                    pubB64 = LocalStore.load(username, "publicKey");
                    CryptoUtil.decodePublicKey(pubB64);
                } catch (Exception exception) {
                    System.err.println("Invalid or corrupted key files for user " + username + ", regenerating keys: " + exception.getMessage());
                    Platform.runLater(() -> new Alert(Alert.AlertType.WARNING,
                            "Your encryption keys were missing or invalid and have been regenerated. You may not be able to read old messages.").showAndWait());
                    KeyPair kp = CryptoUtil.generateRSAKeyPair();
                    myPrivateKey = kp.getPrivate();
                    myPublicKey = kp.getPublic();
                    pubB64 = CryptoUtil.encodePublicKey(kp.getPublic());
                    LocalStore.save(username, "privateKey", CryptoUtil.encodePrivateKey(kp.getPrivate()));
                    LocalStore.save(username, "publicKey", pubB64);
                }
            } else {
                KeyPair kp = CryptoUtil.generateRSAKeyPair();
                myPrivateKey = kp.getPrivate();
                myPublicKey = kp.getPublic();
                pubB64 = CryptoUtil.encodePublicKey(kp.getPublic());
                LocalStore.save(username, "privateKey", CryptoUtil.encodePrivateKey(kp.getPrivate()));
                LocalStore.save(username, "publicKey", pubB64);
            }

            chatClient.send(new Message(
                    MessageType.UPDATE_PUBLIC_KEY,
                    username,
                    username,
                    pubB64
            ));
        } catch (Exception exception) {
            throw new RuntimeException("Key initialization failed for user " + username, exception);
        }

        String[] greetings = {
                "Welcome back, %s!",
                "Hey %s, great to see you!",
                "Hello %s! Ready to chat?",
                "Good to see you, %s!"
        };
        greetingLabel.setText(String.format(
                greetings[new Random().nextInt(greetings.length)], username
        ));

        loadFriends();
        setupFriendCellFactory();
        loadRequests();
        setupRequestCellFactory();
        setupExploreCellFactory();
        setupChatControls();

        int tabs = tabPane.getTabs().size();
        tabPane.setTabMinWidth(0);
        tabPane.tabMaxWidthProperty().bind(tabPane.widthProperty().divide(tabs));
    }

    /* Triggers a user search when the explore search button is clicked */
    @FXML
    private void onExploreSearch() {
        String term = exploreField.getText().trim();
        if (!term.isEmpty()) {
            sendToServer(MessageType.SEARCH_USER, term);
        }
    }

    /* Opens a chat session when a friend is selected from the chat list */
    @FXML
    private void onChatClicked() {
        String selectedFriend = chatList.getSelectionModel().getSelectedItem();
        if (selectedFriend != null) {
            loadChat(selectedFriend);
        }
    }

    /* Configures the send button and chat input field for sending messages */
    private void setupChatControls() {
        if (sendButton == null || chatInput == null) return;
        sendButton.setOnAction(event -> {
            String recipientUsername = chatList.getSelectionModel().getSelectedItem();
            String text = chatInput.getText().trim();
            if (recipientUsername != null && !text.isEmpty()) {
                sendEncryptedChat(recipientUsername, text);
                chatInput.clear();
            }
        });
    }

    /* Requests the server to fetch the list of friends */
    private void loadFriends() {
        sendToServer(MessageType.LIST_FRIENDS, "");
    }

    /* Requests the server to fetch pending friend requests */
    private void loadRequests() {
        sendToServer(MessageType.VIEW_PENDING_REQUESTS, "");
    }

    /* Loads or selects a chat session for the specified friend */
    private void loadChat(String friendName) {
        tabPane.getSelectionModel().select(1);
        if (!chatList.getItems().contains(friendName)) {
            chatList.getItems().add(friendName);
        }
        chatList.getSelectionModel().select(friendName);
        chatList.setPlaceholder(new Label("No messages yet"));
    }

    /* Sets up custom cells for the friend list with a chat button */
    private void setupFriendCellFactory() {
        friendList.setCellFactory(lv -> new ListCell<String>() {
            private final Label nameLabel = new Label();
            private final Button chatBtn = new Button("Chat");
            private final HBox box = new HBox(8, nameLabel, chatBtn);

            {
                nameLabel.getStyleClass().add("return-name-label");
                chatBtn.getStyleClass().addAll("primary-button", "small-primary-button");

                chatBtn.setOnAction(actionEvent -> {
                    String friendName = getItem();
                    if (friendName == null) return;

                    try {
                        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ChatView.fxml"));
                        Parent chatRoot = loader.load();
                        ChatController chatController = loader.getController();
                        chatController.init(chatClient, username, myPrivateKey, myPublicKey, friendPubKeyMap.get(friendName), friendName);
                        Scene chatScene = new Scene(chatRoot, 400, 800);
                        chatScene.getStylesheets().add(getClass().getResource("/client/styles/main.css").toExternalForm());
                        chatScene.getStylesheets().add(getClass().getResource("/client/styles/chat.css").toExternalForm());
                        Stage stage = (Stage) friendList.getScene().getWindow();
                        stage.setScene(chatScene);
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                box.getChildren().setAll(nameLabel, spacer, chatBtn);
            }

            @Override
            protected void updateItem(String friend, boolean empty) {
                super.updateItem(friend, empty);
                if (empty || friend == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(friend);
                    chatBtn.setDisable(false);
                    setGraphic(box);
                }
            }
        });
    }

    private void onChatRequested(String friendName) {
        if (!friendPubKeyMap.containsKey(friendName)) {
            sendToServer(MessageType.REQUEST_PUBLIC_KEY, friendName);
        }
        loadChat(friendName);
    }

    private void setupExploreCellFactory() {
        exploreList.setCellFactory(lv -> new ListCell<String>() {
            private final Label nameLabel = new Label();
            private final Button addBtn = new Button("Add");
            private final Label alreadyLabel = new Label("Friend");
            private final Region spacer = new Region();
            private final HBox box = new HBox(8);

            {
                nameLabel.getStyleClass().add("return-name-label");
                addBtn.getStyleClass().addAll("primary-button", "small-primary-button");
                addBtn.setOnAction(event -> {
                    String targetUser = getItem();
                    if (targetUser != null) {
                        try {
                            chatClient.send(new Message(MessageType.SEND_FRIEND_REQUEST, "", username, targetUser));
                            addBtn.setDisable(true);
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }
                });

                alreadyLabel.getStyleClass().add("return-name-label");
                alreadyLabel.setDisable(true);
                HBox.setHgrow(spacer, Priority.ALWAYS);
            }

            @Override
            protected void updateItem(String usernameItem, boolean empty) {
                super.updateItem(usernameItem, empty);
                if (empty || usernameItem == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(usernameItem);
                    box.getChildren().clear();
                    box.getChildren().addAll(nameLabel, spacer);
                    if (friendList.getItems().contains(usernameItem)) {
                        box.getChildren().add(alreadyLabel);
                    } else {
                        addBtn.setDisable(false);
                        box.getChildren().add(addBtn);
                    }
                    setGraphic(box);
                }
            }
        });
    }

    private void setupRequestCellFactory() {
        requestList.setCellFactory(lv -> new ListCell<String>() {
            private final Label nameLabel = new Label();
            private final Button acceptBtn = new Button("Accept");
            private final Button rejectBtn = new Button("Reject");
            private final HBox box = new HBox(8);

            {
                nameLabel.getStyleClass().add("return-name-label");
                acceptBtn.getStyleClass().addAll("primary-button", "small-primary-button");
                rejectBtn.getStyleClass().addAll("danger-button", "small-danger-button");

                acceptBtn.setOnAction(event -> {
                    String requestSender = getItem();
                    if (requestSender != null) {
                        sendToServer(MessageType.ACCEPT_FRIEND_REQUEST, requestSender);
                        requestList.getItems().remove(requestSender);
                        acceptBtn.setDisable(true);
                        rejectBtn.setDisable(true);
                    }
                });

                rejectBtn.setOnAction(event -> {
                    String requestSender = getItem();
                    if (requestSender != null) {
                        sendToServer(MessageType.REJECT_FRIEND_REQUEST, requestSender);
                        requestList.getItems().remove(requestSender);
                        acceptBtn.setDisable(true);
                        rejectBtn.setDisable(true);
                    }
                });

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                box.getChildren().setAll(nameLabel, spacer, acceptBtn, rejectBtn);
            }

            @Override
            protected void updateItem(String requestItem, boolean empty) {
                super.updateItem(requestItem, empty);
                if (empty || requestItem == null || requestItem.isBlank()) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(requestItem);
                    acceptBtn.setDisable(false);
                    rejectBtn.setDisable(false);
                    setGraphic(box);
                }
            }
        });
    }

    /* Sends an encrypted chat message to the specified recipient */
    private void sendEncryptedChat(String recipientUsername, String messageText) {
        PublicKey pub = friendPubKeyMap.get(recipientUsername);
        if (pub == null) return;
        try {
            String cipher = CryptoUtil.encryptWithPublicKey(messageText, pub);
            chatClient.send(new Message(
                    MessageType.CHAT_MESSAGE,
                    "", username,
                    recipientUsername + ":" + cipher
            ));
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /* Sends a message to the server with the specified type and content */
    private void sendToServer(MessageType type, String content) {
        try {
            chatClient.send(new Message(type, "", username, content));
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    /* Handles incoming server messages and updates the UI accordingly */
    private void onMessageReceived(Message message) {
        Platform.runLater(() -> {
            switch (message.getType()) {
                case PUBLIC_KEY_RESPONSE -> {
                    try {
                        friendPubKeyMap.put(
                                message.getSender(),
                                CryptoUtil.decodePublicKey(message.getContent())
                        );
                    } catch (Exception ignored) {}
                }
                case CHAT_MESSAGE -> {
                    String content = message.getContent();
                    int idx = content.indexOf(':');
                    if (idx < 0) break;
                    String from = message.getSender();
                    String cipherB64 = content.substring(idx + 1);
                    try {
                        String plain = CryptoUtil.decryptWithPrivateKey(cipherB64, myPrivateKey);
                        chatList.getItems().add(from + ": " + plain);
                    } catch (Exception ignored) {}
                }
                case USER_FOUND -> exploreList.getItems().setAll(message.getContent().split(","));
                case USER_NOT_FOUND -> exploreList.getItems().clear();
                case FRIENDS_LIST -> {
                    String content = message.getContent().trim();
                    if (content.isEmpty()) {
                        friendList.getItems().clear();
                    } else {
                        friendList.getItems().setAll(content.split(","));
                    }
                }
                case PENDING_REQUESTS_LIST -> {
                    String content = message.getContent();
                    String incPart = content.split(";", 2)[0];
                    int colon = incPart.indexOf(':');
                    String list;
                    if (colon >= 0 && colon + 1 < incPart.length()) {
                        list = incPart.substring(colon + 1);
                    } else {
                        list = "";
                    }
                    List<String> items;
                    if (list.isEmpty()) {
                        items = List.of();
                    } else {
                        items = List.of(list.split(","));
                    }

                    requestList.getItems().setAll(items);
                }
                case FRIEND_ADDED -> {
                    String newFriend = message.getContent().trim();
                    requestList.getItems().remove(newFriend);
                    List<String> sorted = new ArrayList<>(friendList.getItems());
                    sorted.add(newFriend);
                    sorted.sort(String.CASE_INSENSITIVE_ORDER);
                    friendList.getItems().setAll(sorted);
                }
                case FRIEND_ADD_FAILED -> {
                    new Alert(Alert.AlertType.ERROR,
                            "Could not accept request:\n" + message.getContent())
                            .showAndWait();
                }
                default -> {}
            }
        });
    }

    /* Navigates to the settings screen */
    @FXML
    private void onSettingsClicked() {
        try {
            URL fxmlUrl = getClass().getResource("/client/Settings.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            SettingsController settingsController = loader.getController();
            settingsController.init(chatClient, username);
            Scene scene = new Scene(root, 400, 800);
            URL cssUrl = getClass().getResource("/client/styles/main.css");
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
            Stage stage = (Stage) settingsButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }
}