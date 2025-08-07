CREATE DATABASE IF NOT EXISTS server_messaging_app;
USE server_messaging_app;

-- Users table: Stores user credentials and lockout info
CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    salt VARCHAR(24) NOT NULL,
    public_key_base64 TEXT,
    failed_attempts INT DEFAULT 0,
    lockout_stage INT DEFAULT 0,
    lockout_expiry_ms BIGINT DEFAULT 0
);

-- Friends table: Stores mutual friend relationships (many-to-many)
CREATE TABLE friends (
    user_id INT NOT NULL,
    friend_id INT NOT NULL,
    PRIMARY KEY (user_id, friend_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (friend_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Friend Requests table: Stores pending friend requests
CREATE TABLE friend_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    status ENUM('PENDING', 'ACCEPTED', 'REJECTED') DEFAULT 'PENDING',
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE,
    UNIQUE (sender_id, receiver_id)
);

-- Messages table: Stores undelivered messages
CREATE TABLE messages (
    message_id INT AUTO_INCREMENT PRIMARY KEY,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    cipher TEXT NOT NULL,
    timestamp BIGINT NOT NULL,
    delivered BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (sender_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Indexes for performance
CREATE INDEX idx_messages_sender_receiver ON messages (sender_id, receiver_id);
CREATE INDEX idx_friend_requests_status ON friend_requests (status);