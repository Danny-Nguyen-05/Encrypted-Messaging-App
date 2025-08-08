package client;

import client.Controller.MainController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import shared.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Objects;
import java.util.function.Consumer;

public class ChatClient {
    private Stage primaryStage;
    private String username;

    private final Socket socket;
    private final ObjectOutputStream out;
    private final ObjectInputStream  in;
    private Consumer<Message> onMessage;

    public ChatClient(String host, int port, Consumer<Message> onMessage) throws Exception {
        this.onMessage = onMessage;
        this.socket    = new Socket(host, port);
        this.out       = new ObjectOutputStream(socket.getOutputStream());
        this.in        = new ObjectInputStream(socket.getInputStream());
        startListener();
    }


    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }


    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }



    public void setOnMessage(Consumer<Message> onMessage) {
        this.onMessage = onMessage;
    }

    private void startListener() {
        Thread listener = new Thread(() -> {
            try {
                while (!socket.isClosed()) {
                    Message msg = (Message) in.readObject();
                    if (onMessage != null) {
                        onMessage.accept(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        listener.setDaemon(true);
        listener.start();
    }

    public void send(Message msg) throws Exception {
        out.writeObject(msg);
        out.flush();
    }

    public void close() throws Exception {
        socket.close();
    }
}
