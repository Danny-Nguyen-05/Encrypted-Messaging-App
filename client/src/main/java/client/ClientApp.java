package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // Load the login view
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/client/Login.fxml")
        );
        Parent root = loader.load();

        // Set up and show the scene
        Scene scene = new Scene(root);
        stage.setTitle("Secure Chat Login");
        scene.getStylesheets().add(
                getClass().getResource("/client/styles/login.css").toExternalForm()
        );
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
