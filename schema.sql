-- ==========================================================
-- JECRC StaffNet: College Staff Information & Circular Distribution System
-- Advanced Java Lab Project (JDK 25 + MySQL 8.0)
-- Team: Abhinav Batra, Amit Kumar, Anand Gaur, Abhishek Sihag
-- Guided By: Ms. Mona Nagar (Assistant Professor, CSE Dept)
-- ==========================================================

-- 1. Create Database
CREATE DATABASE IF NOT EXISTS chatapp_db;
USE chatapp_db;

-- 2. Create 'users' Table with Department, Designation, and Role
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    department VARCHAR(100) DEFAULT 'Computer Science & AI',
    designation VARCHAR(100) DEFAULT 'Assistant Professor',
    role VARCHAR(30) DEFAULT 'FACULTY',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create 'messages' Table
-- receiver values:
--  - 'MAIN': Common notice board for all departments (posted by Principal & HODs)
--  - 'DEPT:<DeptName>': Department-specific notice board (e.g., 'DEPT:Information Technology')
--  - '<username>': Confidential 1-to-1 faculty memo
CREATE TABLE IF NOT EXISTS messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    sender VARCHAR(50) NOT NULL,
    receiver VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sender (sender),
    INDEX idx_receiver (receiver),
    INDEX idx_timestamp (timestamp)
);

-- 4. Pre-seeded Staff Accounts (Password for all default accounts: 'admin123')
-- SHA-256 for 'admin123': 240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9
INSERT IGNORE INTO users (username, password_hash, department, designation, role) VALUES 
('principal',      '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Administration',         'Principal',               'ADMIN'),
('mona.nagar',     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Computer Science & AI', 'Head of Department (HOD)', 'HOD'),
('anand.gaur',     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'AI & Data Science',     'Head of Department (HOD)', 'HOD'),
('abhinav.batra',  '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Computer Science & AI', 'Assistant Professor',      'FACULTY'),
('amit.kumar',     '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Information Technology','Assistant Professor',      'FACULTY'),
('abhishek.sihag', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', 'Examination Cell',     'Exam Cell Coordinator',    'ADMIN');

-- Update any existing records to match updated designations
UPDATE users SET designation = 'Principal', department = 'Administration' WHERE username = 'principal';
UPDATE users SET designation = 'Head of Department (HOD)', department = 'Computer Science & AI' WHERE username = 'mona.nagar';
UPDATE users SET designation = 'Head of Department (HOD)', department = 'AI & Data Science' WHERE username = 'anand.gaur';
UPDATE users SET designation = 'Assistant Professor', department = 'Computer Science & AI' WHERE username = 'abhinav.batra';
UPDATE users SET designation = 'Assistant Professor', department = 'Information Technology' WHERE username = 'amit.kumar';
UPDATE users SET designation = 'Exam Cell Coordinator', department = 'Examination Cell' WHERE username = 'abhishek.sihag';

-- 5. Pre-seeded Official Institutional Notices
INSERT INTO messages (sender, receiver, message) VALUES 
('principal',  'MAIN', '[CIRCULAR] Welcome to JECRC StaffNet: Official College Staff Information, Notice & Circular Distribution System (Academic Year 2026-27).'),
('principal',  'MAIN', '[URGENT] NAAC peer-team review scheduled for all departments next week. All HODs to verify course files.'),
('mona.nagar', 'MAIN', '[MEETING] Academic Council Meeting for all department heads on Friday at 3:00 PM in Conference Room 1.'),
('mona.nagar', 'DEPT:Computer Science & AI', '[MEETING] CSE Departmental Faculty Meeting regarding Lab 3 upgrades tomorrow at 11:30 AM.'),
('amit.kumar',  'DEPT:Information Technology', '[CIRCULAR] IT Department 5th semester lab schedule has been finalized. Please review on the portal.');
