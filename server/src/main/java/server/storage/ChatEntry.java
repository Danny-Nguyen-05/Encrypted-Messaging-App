package server.storage;

public class ChatEntry {
    public String sender;
    public String receiver;
    public String cipher;
    public long   timestamp;
    public boolean delivered;


    public ChatEntry() {}

    public ChatEntry(String s, String r, String c, long ts, boolean d) {
        sender    = s;
        receiver  = r;
        cipher    = c;
        timestamp = ts;
        delivered = d;
    }
}
