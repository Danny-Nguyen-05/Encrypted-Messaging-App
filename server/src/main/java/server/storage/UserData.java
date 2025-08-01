package server.storage;

public class UserData {
    public String username;
    public String passwordHash;
    public String salt;

    public String publicKeyBase64;

    public int    failedAttempts   = 0;
    public long   lockoutExpiryMs  = 0L;
    public int    lockoutStage     = 0;

    public UserData() {}

    public UserData(String username, String passwordHash, String salt) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.salt = salt;
    }
}