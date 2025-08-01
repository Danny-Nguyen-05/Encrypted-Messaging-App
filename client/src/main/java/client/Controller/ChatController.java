package client.Controller;

import client.ChatClient;
import client.crypto.CryptoUtil;
import client.crypto.LocalStore;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import shared.Message;
import shared.MessageType;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;


public class ChatController {
    @FXML private Button backButton;
    @FXML private Label chatWithLabel;
    @FXML private ListView<String> messageList;
    @FXML private TextField messageField;
    @FXML private Button sendButton;

    private ChatClient chatClient;
    private String username;
    private String peerName;
    private PrivateKey myPrivateKey;
    private PublicKey myPublicKey;
    private PublicKey peerPublicKey;
    private boolean isInChat = true;

    // Initializes the chat with client and user details
    public void init(ChatClient chatClient, String username, PrivateKey myPrivateKey,
                     PublicKey myPublicKey, PublicKey peerPublicKey, String peerName) throws Exception {
        this.chatClient = chatClient;
        this.username = username;
        this.myPrivateKey = myPrivateKey;
        this.myPublicKey = myPublicKey;
        this.peerPublicKey = peerPublicKey;
        this.peerName = peerName;

        chatWithLabel.setText(peerName);
        sendButton.setDisable(peerPublicKey == null);

        if (peerPublicKey == null) {
            chatClient.send(new Message(MessageType.REQUEST_PUBLIC_KEY, this.username, peerName, ""));
        }

        isInChat = true;
        chatClient.send(new Message(MessageType.CHAT_STATE_UPDATE, username, peerName, "IN_CHAT"));
        chatClient.setOnMessage(this::onMessageReceived);
        loadLocalChatHistory();
        chatClient.send(new Message(MessageType.HISTORY_REQUEST, username, peerName, ""));

        Platform.runLater(() -> {
            int lastIndex = messageList.getItems().size() - 1;
            if (lastIndex >= 0) {
                messageList.scrollTo(lastIndex);
            }
        });

        sendButton.setOnAction(this::onSendClicked);
        messageField.setOnAction(this::onSendClicked);
        setupBubbleFactory();

        messageList.getItems().addListener((ListChangeListener<String>) change -> {
            while (change.next() && change.wasAdded()) {
                Platform.runLater(() -> {
                    int lastIndex = messageList.getItems().size() - 1;
                    if (lastIndex >= 0) {
                        messageList.scrollTo(lastIndex);
                    }
                });
            }
        });
    }

    // Formatter for timestamps
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                    .withLocale(Locale.getDefault())
                    .withZone(ZoneId.systemDefault());

    // Formats epoch time to readable string
    private String fmt(long epochMillis) {
        return TS_FMT.format(Instant.ofEpochMilli(epochMillis));
    }

    // Loads and displays chat history from local storage
    private void loadLocalChatHistory() {
        try {
            List<LocalStore.ChatMessageEntry> messages = LocalStore.loadChatMessages(username, peerName);
            for (LocalStore.ChatMessageEntry msg : messages) {
                String plain = CryptoUtil.decryptWithPrivateKey(msg.cipher, myPrivateKey);
                String when = fmt(msg.timestamp);
                messageList.getItems().add(msg.sender + ": " + plain + "\n" + when);
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Chat History Error");
            alert.setHeaderText("Failed to load chat history");
            alert.setContentText("Some messages may not be displayed: " + e.getMessage());
            alert.showAndWait();
        }
    }

    // Sets up custom cell factory for chat bubble display
    private void setupBubbleFactory() {
        messageList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                String[] lines = item.split("\n", 2);
                String firstLine = lines[0];
                String tsLine = lines.length > 1 ? lines[1] : "";
                String[] parts = firstLine.split(": ", 2);
                String sender = parts[0];
                String text = parts.length > 1 ? parts[1] : "";

                Label tsLabel = new Label(tsLine);
                tsLabel.getStyleClass().add("timestamp-label");

                Label bubble = new Label(text);
                bubble.setWrapText(true);
                bubble.getStyleClass().add("chat-bubble");
                bubble.getStyleClass().add(sender.equals(username) ? "me" : "friend");
                bubble.maxWidthProperty().bind(
                        messageList.widthProperty().subtract(20).multiply(0.6)
                );
                bubble.setMinWidth(Region.USE_COMPUTED_SIZE);

                VBox vbox = new VBox(2, tsLabel, bubble);
                vbox.setAlignment(sender.equals(username) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                vbox.setFillWidth(false);

                setGraphic(vbox);
            }
        });
    }

    // Handles sending a message
    @FXML
    private void onSendClicked(ActionEvent e) {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;

        if (peerPublicKey == null) {
            try {
                chatClient.send(new Message(MessageType.REQUEST_PUBLIC_KEY, username, peerName, ""));
            } catch (Exception ex) {
                System.err.println("Error requesting public key: " + ex.getMessage());
            }
            return;
        }

        try {
            String cipher = CryptoUtil.encryptWithPublicKey(text, peerPublicKey);
            String localCipher = CryptoUtil.encryptWithPublicKey(text, myPublicKey);
            long timestamp = System.currentTimeMillis();

            LocalStore.saveChatMessage(username, peerName, username, peerName, localCipher, timestamp);
            chatClient.send(new Message(MessageType.CHAT_MESSAGE, username, peerName, cipher + "|" + (isInChat ? "IN_CHAT" : "NOT_IN_CHAT")));

            String when = fmt(timestamp);
            messageList.getItems().add(username + ": " + text + "\n" + when);
            messageField.clear();
        } catch (Exception ex) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Send Error");
            alert.setHeaderText("Failed to send message");
            alert.setContentText("Error: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    // Processes incoming messages
    private void onMessageReceived(Message msg) {
        Platform.runLater(() -> {
            try {
                switch (msg.getType()) {
                    case PUBLIC_KEY_RESPONSE -> {
                        String keyContent = msg.getContent();
                        if (keyContent == null || keyContent.trim().isEmpty() || keyContent.equals("NO_KEY") || keyContent.equals("USER_NOT_FOUND")) {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Key Exchange Error");
                            alert.setHeaderText("Failed to receive public key");
                            alert.setContentText("The server returned an invalid public key for " + peerName);
                            alert.showAndWait();
                            return;
                        }
                        this.peerPublicKey = CryptoUtil.decodePublicKey(keyContent);
                        sendButton.setDisable(false);
                    }
                    case CHAT_MESSAGE, HISTORY_RESPONSE -> {
                        String[] parts = msg.getContent().split("\\|", 2);
                        String cipherB64 = parts[0];
                        String plain = CryptoUtil.decryptWithPrivateKey(cipherB64, myPrivateKey);
                        long timestamp = System.currentTimeMillis();

                        LocalStore.saveChatMessage(username, peerName, msg.getSender(), msg.getReceiver(), cipherB64, timestamp);

                        String when = fmt(timestamp);
                        messageList.getItems().add(msg.getSender() + ": " + plain + "\n" + when);
                    }
                    default -> System.out.println("Received message of type: " + msg.getType());
                }
            } catch (Exception ex) {
                if (msg.getType() == MessageType.CHAT_MESSAGE || msg.getType() == MessageType.HISTORY_RESPONSE) {
                    messageList.getItems().add("ERROR: Could not decrypt message from " + msg.getSender());
                }
            }
        });
    }

    // Handles navigation back to main screen
    @FXML
    private void onBackClicked() {
        try {
            isInChat = false;
            chatClient.send(new Message(MessageType.CHAT_STATE_UPDATE, username, peerName, "NOT_IN_CHAT"));
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/MainTabs.fxml"));
            Parent root = loader.load();
            MainController mc = loader.getController();
            mc.init(chatClient, username);
            Scene scene = new Scene(root, 400, 800);
            scene.getStylesheets().add(getClass().getResource("/client/styles/main.css").toExternalForm());
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            System.err.println("Error navigating back: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}