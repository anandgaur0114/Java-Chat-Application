-- ==========================================================
-- JECRC StaffNet: College Staff Information & Circular Distribution System
-- Advanced Java Lab Project (JDK 25 + MySQL 8.0)
-- Team: Abhinav Batra, Amit Kumar, Anand Gaur, Abhishek Sihag
-- Guided By: Ms. Mona Nagar (Assistant Professor, CSE Dept)
-- ==========================================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS chatapp_db;
USE chatapp_db;

-- 2. Create 'users' Table with Department & Designation for Staff
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    department VARCHAR(100) DEFAULT 'Computer Science & Engineering',
    designation VARCHAR(100) DEFAULT 'Assistant Professor',
    role VARCHAR(30) DEFAULT 'FACULTY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create 'messages' Table for Notices & Confidential Staff Memos
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(50) NOT NULL, -- 'ALL' for Notice Board broadcast or specific staff username
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender),
    INDEX idx_receiver (receiver),
    INDEX idx_timestamp (timestamp)
);

-- 4. Pre-seeded Staff Accounts (Password for all default accounts: 'admin123')
-- SHA-256 for 'admin123': 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
INSERT IGNORE INTO users (username, password_hash, department, designation, role) VALUES 
('mona.nagar',     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Computer Science & AI',      'Assistant Professor & Project Guide', 'HOD'),
('abhinav.batra',  '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Computer Science & AI',      'Assistant Professor & Exam Cell',    'FACULTY'),
('anand.gaur',     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'AI & Data Science',          'Head of Department (HOD)',          'HOD'),
('amit.kumar',     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'AI & Computing Labs',        'Senior Lab Incharge',               'STAFF'),
('abhishek.sihag', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Examination Cell',          'Academic Controller',               'ADMIN'),
('principal.office','240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Principal Secretariat',     'Executive Administrator',           'ADMIN');

-- 5. Pre-seeded Official Institutional Notices
INSERT INTO messages (sender, receiver, message) VALUES 
('principal.office', 'ALL', '[CIRCULAR] Welcome to JECRC StaffNet: Official College Staff Information, Notice & Circular Distribution System (Academic Year 2026-27).'),
('mona.nagar',       'ALL', '[MEETING] Academic Review & Project Progress meeting for 3rd Year CSE (AI) scheduled for Friday at 2:30 PM in Lab 3.'),
('abhishek.sihag',   'ALL', '[EXAM] Faculty invigilation duty rosters for upcoming Mid-Term examinations are now published. Please verify your slots.'),
('anand.gaur',       'ALL', '[URGENT] NAAC Accreditation criterion documentation verification tomorrow morning at 10:00 AM. All department files must be updated.');
