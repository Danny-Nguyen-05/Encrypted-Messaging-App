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

public class RegisterController {
    @FXML TextField usernameField;
    @FXML PasswordField passwordField, confirmField;
    @FXML Label statusLabel;
    @FXML Button registerButton;

    private ChatClient chatClient;

    /* Sets up the registration UI, disabling the register button if any field is empty */
    public void initialize() {
        registerButton.disableProperty().bind(
                usernameField.textProperty().isEmpty()
                        .or(passwordField.textProperty().isEmpty())
                        .or(confirmField.textProperty().isEmpty())
        );
    }

    /* Navigates to the login scene when the login link is clicked */
    @FXML
    private void onShowLogin() throws Exception {
        switchScene("/client/Login.fxml", "/client/styles/login.css");
    }

    /* Handles the registration process when the sign-up button is clicked */
    @FXML
    private void onSignUpClicked() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmField.getText();

        if (!password.equals(confirmPassword)) {
            statusLabel.setText("Passwords do not match.");
            return;
        }

        if (chatClient == null) {
            try {
                chatClient = new ChatClient("localhost", 12345, this::onMessage);
            } catch (Exception ex) {
                statusLabel.setText("Cannot connect.");
                return;
            }
        }

        statusLabel.setText("Registering…");
        new Thread(() -> {
            try {
                chatClient.send(new Message(
                        MessageType.REGISTER, "", username, password
                ));
            } catch (Exception ex) {
                Platform.runLater(() ->
                        statusLabel.setText("Network error.")
                );
            }
        }).start();
    }

    /* Processes server responses for registration attempts */
    private void onMessage(Message msg) {
        Platform.runLater(() -> {
            if (msg.getType() == MessageType.REGISTER_SUCCESS) {
                try {
                    onShowLogin();
                } catch (Exception e) {
                    statusLabel.setText("Error loading login.");
                }
            } else if (msg.getType() == MessageType.REGISTER_FAILURE) {
                statusLabel.setText(msg.getContent());
            }
        });
    }

    /* Loads and switches to a new scene with specified FXML and CSS files */
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