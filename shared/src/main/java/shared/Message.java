package shared;

import java.io.Serializable;

public class Message implements Serializable {
    private MessageType type;
    private String sender;
    private String receiver;
    private String content;

    public Message(MessageType type, String sender, String receiver, String content) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
    }

    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }

    public void setType(MessageType type) { this.type = type; }
    public void setSender(String sender) { this.sender = sender; }
    public void setReceiver(String receiver) { this.receiver = receiver; }
    public void setContent(String content) { this.content = content; }
}