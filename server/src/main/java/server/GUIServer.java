package server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class GUIServer extends Application {
    private static TextArea logArea;
    private static final int PORT = 12345;

    private ServerSocket serverSocket;
    private final CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        launch(args); // Launch JavaFX GUI
    }

    @Override
    public void start(Stage primaryStage) {
        logArea = new TextArea();
        logArea.setEditable(false);

        BorderPane root = new BorderPane();
        root.setCenter(logArea);

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Secure Messaging Server");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            stopServer();  // Called when window closes
            Platform.exit();
        });
        primaryStage.show();

        startServer();
    }

    private void startServer() {
        Thread serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(PORT)) {
                this.serverSocket = ss;
                log("Server started on port " + PORT);

                while (!ss.isClosed()) {
                    Socket clientSocket = ss.accept();
                    ClientHandler handler = new ClientHandler(clientSocket);
                    clientHandlers.add(handler);
                    new Thread(handler).start();
                }
            } catch (IOException e) {
                log("Server stopped or error: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void stopServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();  // Stop accepting new connections
                log("Server socket closed.");
            }

            for (ClientHandler handler : clientHandlers) {
                handler.shutdown(); // You must implement this in ClientHandler
            }

            log("All clients disconnected.");
        } catch (IOException e) {
            log("Error shutting down server: " + e.getMessage());
        }
    }

    public static void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }
}
