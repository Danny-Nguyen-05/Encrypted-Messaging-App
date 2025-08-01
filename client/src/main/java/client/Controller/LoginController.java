package client.Controller;

import client.ChatClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import shared.Message;
import shared.MessageType;

import java.io.IOException;

public class LoginController {
    @FXML TextField usernameField;
    @FXML PasswordField passwordField;
    @FXML Button loginButton;
    @FXML Label statusLabel;

    private ChatClient chatClient;

    // Initializes the login interface
    @FXML
    public void initialize() {
        // Disable login button if username or password is empty
        loginButton.disableProperty().bind(
                usernameField.textProperty().isEmpty()
                        .or(passwordField.textProperty().isEmpty())
        );
    }

    // Navigates to the registration scene
    @FXML
    void onShowRegister() throws Exception {
        switchScene("/client/Register.fxml", "/client/styles/register.css");
    }

    // Handles login button click
    @FXML
    void onLoginClicked() {
        String u = usernameField.getText().trim();
        String p = passwordField.getText();
        statusLabel.setText("");
        if (u.isEmpty() || p.isEmpty()) {
            statusLabel.setText("Username & password required.");
            return;
        }

        // Initialize client connection if not already done
        if (chatClient == null) {
            try {
                chatClient = new ChatClient("localhost", 12345, this::onMessage);
            } catch (Exception ex) {
                statusLabel.setText("Cannot connect to server.");
                return;
            }
        }

        statusLabel.setText("Logging in…");
        // Send login request in a separate thread
        new Thread(() -> {
            try {
                chatClient.send(new Message(
                        MessageType.LOGIN, "", u, p));
            } catch (Exception ex) {
                Platform.runLater(() -> statusLabel.setText("Network error."));
            }
        }).start();
    }

    // Processes incoming server messages
    private void onMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case LOGIN_SUCCESS -> {
                    try {
                        // Load main dashboard
                        FXMLLoader loader = new FXMLLoader(
                                getClass().getResource("/client/MainTabs.fxml")
                        );
                        Parent dashboard = loader.load();

                        // Initialize main controller
                        MainController mainCtrl = loader.getController();
                        mainCtrl.init(chatClient, msg.getReceiver());

                        // Create and configure new scene
                        Scene scene = new Scene(dashboard, 400, 800);
                        scene.getStylesheets().add(
                                getClass().getResource("/client/styles/main.css")
                                        .toExternalForm()
                        );

                        // Switch to main scene
                        Stage stage = (Stage) usernameField.getScene().getWindow();
                        stage.setScene(scene);
                    } catch (IOException e) {
                        e.printStackTrace();
                        statusLabel.setText("Error loading main view");
                    }
                }
                case LOGIN_FAILURE -> {
                    statusLabel.setText(msg.getContent());
                }
                default -> { /* Ignore other message types */ }
            }
        });
    }

    // Switches to a new scene with specified FXML and CSS
    private void switchScene(String fxmlPath, String cssPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent root = loader.load();
            Scene scene = new Scene(root, 400, 800);
            scene.getStylesheets().add(
                    getClass().getResource(cssPath).toExternalForm()
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}