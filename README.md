# 🔐 Encrypted Messaging App

A **secure, end-to-end encrypted messaging application** built in **Java**, using **JavaFX** for the UI, **Maven** for build & dependency management, and **MySQL** for server-side storage.

> **Security-first:** Messages are encrypted using **RSA** (2048-bit) with **OAEP (SHA-256 + MGF1)** *before* they leave the client. Only the intended recipient—holding the private key—can decrypt them.

---

## ✨ Features

- 🔑 **Encryption (Main Feature)**
  - End-to-end **RSA** with `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`
  - 2048-bit key pairs, Base64-encoded ciphertexts
  - Keys generated per user and stored locally
  - Clean handling of URL-safe Base64 variants
- 👥 **User & Friend Management**
  - Register & log in (hashed + salted passwords)
  - Search users, send/accept friend requests
- 💬 **Messaging**
  - One-to-one chat after mutual acceptance
  - Local chat history persisted
- 🎨 **JavaFX + CSS UI**
  - FXML views styled with CSS (e.g., `chat.css`, `login.css`, `main.css`, `register.css`)
- 🛠 **Maven**
  - Dependency management (JavaFX, MySQL Connector, Jackson, etc.)
  - Reproducible builds and run targets

---

## 🧰 Tech Stack

| Component        | Technology / Library |
|------------------|---------------------|
| **Language**     | Java (17+) |
| **UI**           | JavaFX 23 (Controls + FXML), CSS |
| **Build Tool**   | Maven (multi-module: `client`, `server`, `shared`) |
| **Backend**      | Custom Java socket server (auth, friends, message routing) |
| **Database**     | MySQL (server: users/friends/requests/undelivered messages; client: local chat history) |
| **Local Storage**| **Key files** under `client/keys/` (via `LocalStore`) |
| **Data Handling**| Jackson Databind (utility/optional) |
| **Encryption**   | RSA (2048-bit, OAEP-SHA256) |

---

## 📦 Dependencies (Managed by Maven)

| Dependency | Purpose |
|-----------|---------|
| **JavaFX (Controls, FXML)** | UI components & layouts |
| **MySQL Connector/J** | JDBC driver to connect to MySQL |
| **Jackson Databind** | General object–JSON mapping if needed |
| **Shared Module** | Common classes (`Message`, `MessageType`) used by client & server |
| **JavaFX Maven Plugin** | Enables `mvn javafx:run` to launch the client |

---

## 🏗 Project Structure

```
client/
├─ src/main/java/client/
│  ├─ Controller/                 # ChatController, LoginController, MainController, RegisterController, SettingsController
│  ├─ crypto/                     # CryptoUtil (RSA encryption/decryption), LocalStore (key file storage + DB message save/load)
│  ├─ keys/                       # Per-user key files (e.g., alice_privateKey.txt, alice_publicKey.txt)
│  ├─ ChatClient.java
│  ├─ ClientApp.java              # Client entry point
│  └─ ClientDatabaseStore.java
│
├─ src/main/resources/client/
│  ├─ images/                     # UI image assets
│  ├─ styles/                     # chat.css, login.css, main.css, register.css
│  ├─ ChatView.fxml
│  ├─ Login.fxml
│  ├─ MainTabs.fxml
│  ├─ Register.fxml
│  └─ Settings.fxml
│
server/
├─ src/main/java/server/
│  ├─ storage/                    # Server-side persistence and data classes
│  │  ├─ ChatEntry.java
│  │  ├─ DatabaseStore.java
│  │  ├─ FriendData.java
│  │  ├─ FriendRequestData.java
│  │  ├─ MessageStore.java
│  │  └─ UserData.java
│  ├─ ClientHandler.java
│  ├─ FriendManager.java
│  ├─ GUIServer.java
│  ├─ PasswordUtil.java
│  ├─ ServerMain.java             # Server entry point
│  └─ UserManager.java
│
├─ src/main/resources/server.storage/
│  ├─ application.properties
│  └─ application.properties.example
│
database/sql/
├─ client_schema.sql              # Defines local client database (chat history, users)
└─ server_schema.sql              # Defines server database (users, friends, requests, messages)
│
shared/
└─ src/main/java/shared/
   ├─ Message.java
   └─ MessageType.java

```




---

## ⚙️ Configuration

Create/update `server/src/main/resources/application.properties`:
```properties
db.url=jdbc:mysql://localhost:3306/server_messaging_app
db.username=YOUR_DB_USER
db.password=YOUR_DB_PASSWORD
```

---

## 🚀 Build & Run (Maven)

**Start the server**
```bash
cd server
mvn clean install
mvn exec:java -Dexec.mainClass="server.ServerMain"
```

**Start the client (JavaFX)**
```bash
cd client
mvn clean install
mvn javafx:run
```

> If JavaFX isn’t bundled with your JDK, ensure you have the JavaFX Maven plugin and dependencies in `client/pom.xml`.

---

## 🖼 Demo Screenshots


| Login Screen | Register Screen | Chat View | Friend Requests | Friend Searching | Settings |
|---|---|---|---|---|---|
| <img width="600" height="940" alt="Login Screen" src="https://github.com/user-attachments/assets/e819277e-2328-4deb-af7d-c9f3c5ec590e" /> | <img width="550" height="940" alt="Register Screen" src="https://github.com/user-attachments/assets/d74469bd-c066-4e87-8b4c-472bc0d30081" /> | <img width="700" height="980" alt="Chat View" src="https://github.com/user-attachments/assets/fa6c827c-f07c-4fd9-87f5-9e584415535f" /> | <img width="512" height="940" alt="Friend Requests" src="https://github.com/user-attachments/assets/5d7d1fa7-533e-4f65-86f2-408bda3f1b08" /> | <img width="500" height="940" alt="Friend Searching" src="https://github.com/user-attachments/assets/70991b6e-c459-4387-bec9-08eb1c98c675" /> | <img width="550" height="940" alt="Settings" src="https://github.com/user-attachments/assets/34cdd078-fac4-46eb-932e-2f1a1e5ae11e" />



---

## 🌐 Deployment Status

- **Current:** Local-only (client and server run on your laptop / localhost)
- **Future:** Host the server on a public endpoint (e.g., AWS EC2/Lightsail, Render, a VPS). Clients will connect using the server’s public IP/hostname over TLS.

### Planned Internet Deployment (High-Level)
1. Package server as a runnable JAR
2. Provision a Linux VM (Ubuntu) in the cloud
3. Install Java & MySQL (or use managed DB)
4. Configure firewall/security groups to allow the server port
5. Point clients to the public `host:port`
6. Add TLS (reverse proxy like Nginx or direct TLS sockets)

---

## 📌 Roadmap

- [ ] Cloud deployment for internet access
- [ ] **Hybrid crypto** (RSA + AES‑GCM) with per-message IVs
- [ ] Group chats & typing indicators
- [ ] File/media attachments
- [ ] Message search and pagination
