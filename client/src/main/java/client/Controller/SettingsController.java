package client.Controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import shared.Message;
import shared.MessageType;
import client.ChatClient;
import java.io.IOException;
import java.net.URL;

public class SettingsController {
    @FXML private Button backButton;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;

    private ChatClient chatClient;
    private String username;

    /* Initializes the settings controller with client and user details */
    public void init(ChatClient client, String user) {
        this.chatClient = client;
        this.username = user;
        chatClient.setOnMessage(this::onServerMessage);
    }

    /* Navigates back to the main application interface */
    @FXML
    private void onBackClicked() {
        try {
            URL fxml = getClass().getResource("/client/MainTabs.fxml");
            FXMLLoader loader = new FXMLLoader(fxml);
            Parent root = loader.load();
            MainController mc = loader.getController();
            mc.init(chatClient, username);
            Scene scene = new Scene(root, 400, 800);
            scene.getStylesheets().add(
                    getClass().getResource("/client/styles/main.css").toExternalForm()
            );
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* Handles password change request with confirmation dialog */
    @FXML
    private void onChangePassword() throws Exception {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Password Change");
        confirm.setHeaderText("Are you sure you want to change your password?");
        confirm.setContentText("If you proceed, you will be logged out.");
        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(yes, no);

        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() == no) {
            return;
        }

        String oldPw = oldPasswordField.getText();
        String newPw = newPasswordField.getText();
        chatClient.send(new Message(
                MessageType.CHANGE_PASSWORD,
                "", username,
                oldPw + ":" + newPw
        ));
    }

    /* Processes server responses for settings-related actions */
    private void onServerMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getType()) {
                case CHANGE_PASSWORD_SUCCESS -> {
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Password Changed");
                    info.setHeaderText(null);
                    info.setContentText(msg.getContent());
                    info.showAndWait();
                    onLogoutClicked();
                }
                case CHANGE_PASSWORD_FAILURE -> {
                    Alert err = new Alert(Alert.AlertType.ERROR);
                    err.setTitle("Change Password Failed");
                    err.setHeaderText(null);
                    err.setContentText(msg.getContent());
                    err.showAndWait();
                }
                case LOGOUT_SUCCESS -> {
                    try {
                        chatClient.close();
                    } catch (Exception ignored) {}
                    Parent loginRoot;
                    try {
                        loginRoot = FXMLLoader.load(
                                getClass().getResource("/client/Login.fxml")
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    Scene scene = new Scene(loginRoot, 400, 800);
                    scene.getStylesheets().add(
                            getClass().getResource("/client/styles/login.css")
                                    .toExternalForm()
                    );
                    Stage stage = (Stage) backButton.getScene().getWindow();
                    stage.setScene(scene);
                }
                default -> {}
            }
        });
    }

    /* Initiates logout process by sending a logout message to the server */
    @FXML
    private void onLogoutClicked() {
        try {
            chatClient.send(new Message(
                    MessageType.LOGOUT, "", username, ""
            ));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}