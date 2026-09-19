-- ==========================================================
-- Advanced Java Lab Project: Client-Server Chat Application
-- Database Setup Script (MySQL 8.0)
-- ==========================================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS chatapp_db;
USE chatapp_db;

-- 2. Create 'users' Table
-- Stores user credentials with SHA-256 hashed passwords
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create 'messages' Table
-- Stores both group chat (receiver = 'ALL') and private 1-to-1 messages
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NOT NULL, -- 'ALL' for broadcast or recipient's username
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender),
    INDEX idx_receiver (receiver),
    INDEX idx_timestamp (timestamp)
);

-- Sample Test Data (Optional, passwords are SHA-256 for 'password123')
-- SHA-256 for 'password123': ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f
INSERT IGNORE INTO users (username, password_hash) VALUES 
('Alice', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f'),
('Bob', 'ef92b778bafe771e89245b89ecbc08a44a4e166c06659911881f383d4473e94f');

-- Sample Welcome Message
INSERT INTO messages (sender, receiver, message) VALUES 
('System', 'ALL', 'Welcome to the Java Chat Server! Chat history is active.');
