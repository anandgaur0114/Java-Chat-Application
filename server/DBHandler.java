package server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * DBHandler manages all JDBC interactions with the MySQL database
 * for the JECRC College Staff Information & Circular Distribution System.
 * Demonstrates:
 *  - External configuration via Properties file with sensible defaults
 *  - Department & Authority aware schema (Principal, HOD, Faculty)
 *  - Partitioned message retrieval: Main Notice Board vs Department Notice Board vs Private Memos
 *  - PreparedStatements to prevent SQL Injection
 *  - SHA-256 Hashing for secure password storage
 */
public class DBHandler {

    // Default connection parameters
    private static String dbUrl = "jdbc:mysql://localhost:3306/chatapp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static String dbUser = "root";
    private static String dbPassword = "root";

    static {
        loadConfig();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBHandler] MySQL JDBC Driver not found in classpath! Check lib/ folder.");
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        File propFile = new File("db.properties");
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                Properties props = new Properties();
                props.load(fis);
                dbUrl = props.getProperty("db.url", dbUrl);
                dbUser = props.getProperty("db.user", dbUser);
                dbPassword = props.getProperty("db.password", dbPassword);
                System.out.println("[DBHandler] Loaded database configuration from db.properties.");
            } catch (IOException e) {
                System.err.println("[DBHandler] Warning: Could not read db.properties, using defaults.");
            }
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DBHandler] Connection failed: " + e.getMessage());
            System.err.println("[DBHandler] Check URL: " + dbUrl + " | User: " + dbUser);
            return false;
        }
    }

    /**
     * Checks if a staff member has administrative authority (Principal or HOD)
     * permitted to post broadcast announcements on the Main Campus Notice Board.
     */
    public static boolean isAuthority(String designation) {
        if (designation == null) return false;
        String d = designation.trim().toLowerCase();
        return d.contains("principal") || d.contains("hod") || d.contains("head of department");
    }

    /**
     * Auto-migrates schema to ensure staff columns and partitioned receivers exist,
     * and seeds institutional staff accounts.
     */
    public static void initializeStaffSystem() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Create tables if not exist
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "department VARCHAR(100) DEFAULT 'Computer Science & AI', " +
                "designation VARCHAR(100) DEFAULT 'Assistant Professor', " +
                "role VARCHAR(30) DEFAULT 'FACULTY', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "sender VARCHAR(50) NOT NULL, " +
                "receiver VARCHAR(100) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_sender (sender), " +
                "INDEX idx_receiver (receiver), " +
                "INDEX idx_timestamp (timestamp))"
            );

            // Safe column alterations
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN department VARCHAR(100) DEFAULT 'Computer Science & AI'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN designation VARCHAR(100) DEFAULT 'Assistant Professor'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN role VARCHAR(30) DEFAULT 'FACULTY'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE messages MODIFY receiver VARCHAR(100) NOT NULL"); } catch (SQLException ignored) {}

            // 2. Pre-seed default faculty & staff accounts (password: admin123)
            String defaultPassHash = hashPassword("admin123");
            String seedUsersSql = 
                "INSERT IGNORE INTO users (username, password_hash, department, designation, role) VALUES " +
                "('principal', '" + defaultPassHash + "', 'Administration', 'Principal', 'ADMIN')," +
                "('mona.nagar', '" + defaultPassHash + "', 'Computer Science & AI', 'Head of Department (HOD)', 'HOD')," +
                "('anand.gaur', '" + defaultPassHash + "', 'AI & Data Science', 'Head of Department (HOD)', 'HOD')," +
                "('abhinav.batra', '" + defaultPassHash + "', 'Computer Science & AI', 'Assistant Professor', 'FACULTY')," +
                "('amit.kumar', '" + defaultPassHash + "', 'Information Technology', 'Assistant Professor', 'FACULTY')," +
                "('abhishek.sihag', '" + defaultPassHash + "', 'Examination Cell', 'Exam Cell Coordinator', 'ADMIN')";
            try {
                stmt.executeUpdate(seedUsersSql);
            } catch (SQLException ignored) {}

            // Update designations for existing users if any
            try {
                stmt.executeUpdate("UPDATE users SET designation = 'Principal', department = 'Administration' WHERE username = 'principal'");
                stmt.executeUpdate("UPDATE users SET designation = 'Head of Department (HOD)', department = 'Computer Science & AI' WHERE username = 'mona.nagar'");
                stmt.executeUpdate("UPDATE users SET designation = 'Head of Department (HOD)', department = 'AI & Data Science' WHERE username = 'anand.gaur'");
                stmt.executeUpdate("UPDATE users SET designation = 'Assistant Professor', department = 'Computer Science & AI' WHERE username = 'abhinav.batra'");
                stmt.executeUpdate("UPDATE users SET designation = 'Assistant Professor', department = 'Information Technology' WHERE username = 'amit.kumar'");
                stmt.executeUpdate("UPDATE users SET designation = 'Exam Cell Coordinator', department = 'Examination Cell' WHERE username = 'abhishek.sihag'");
            } catch (SQLException ignored) {}

            // 3. Seed initial notices if empty
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                        "INSERT INTO messages (sender, receiver, message) VALUES " +
                        "('principal', 'MAIN', '[CIRCULAR] Welcome to JECRC StaffNet: Official College Staff Information, Notice & Circular Distribution System (Academic Year 2026-27).')," +
                        "('principal', 'MAIN', '[URGENT] NAAC peer-team review scheduled for all departments next week. All HODs to verify course files.')," +
                        "('mona.nagar', 'MAIN', '[MEETING] Academic Council Meeting for all department heads on Friday at 3:00 PM in Conference Room 1.')," +
                        "('mona.nagar', 'DEPT:Computer Science & AI', '[MEETING] CSE Departmental Faculty Meeting regarding Lab 3 upgrades tomorrow at 11:30 AM.')," +
                        "('amit.kumar', 'DEPT:Information Technology', '[CIRCULAR] IT Department 5th semester lab schedule has been finalized. Please review on the portal.')"
                    );
                }
            } catch (SQLException ignored) {}

            System.out.println("[DBHandler] StaffNet schema initialized with Department & Authority partition.");
        } catch (SQLException e) {
            System.err.println("[DBHandler] Schema initialization notice: " + e.getMessage());
        }
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in current JRE", e);
        }
    }

    public static class StaffProfile {
        public final String username;
        public final String department;
        public final String designation;
        public final String role;

        public StaffProfile(String username, String department, String designation, String role) {
            this.username = username;
            this.department = (department != null && !department.trim().isEmpty()) ? department.trim() : "Computer Science & AI";
            this.designation = (designation != null && !designation.trim().isEmpty()) ? designation.trim() : "Assistant Professor";
            this.role = (role != null && !role.trim().isEmpty()) ? role.trim() : "FACULTY";
        }
    }

    public static StaffProfile getStaffProfile(String username) {
        if (username == null) {
            return new StaffProfile("Unknown", "Computer Science & AI", "Assistant Professor", "FACULTY");
        }
        String sql = "SELECT username, department, designation, role FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new StaffProfile(
                        rs.getString("username"),
                        rs.getString("department"),
                        rs.getString("designation"),
                        rs.getString("role")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBHandler] Profile lookup notice: " + e.getMessage());
        }
        return new StaffProfile(username, "Computer Science & AI", "Assistant Professor", "FACULTY");
    }

    public static boolean registerUser(String username, String password, String department, String designation, String role) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        String dept = (department != null && !department.trim().isEmpty()) ? department.trim() : "Computer Science & AI";
        String desig = (designation != null && !designation.trim().isEmpty()) ? designation.trim() : "Assistant Professor";
        String r = (role != null && !role.trim().isEmpty()) ? role.trim() : "FACULTY";

        String sql = "INSERT INTO users (username, password_hash, department, designation, role) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username.trim());
            pstmt.setString(2, hashPassword(password));
            pstmt.setString(3, dept);
            pstmt.setString(4, desig);
            pstmt.setString(5, r);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DBHandler] Registration error: " + e.getMessage());
            return false;
        }
    }

    public static boolean registerUser(String username, String password) {
        return registerUser(username, password, "Computer Science & AI", "Assistant Professor", "FACULTY");
    }

    public static boolean authenticateUser(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        String sql = "SELECT password_hash FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("password_hash");
                    String inputHash = hashPassword(password);
                    return storedHash.equals(inputHash);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBHandler] Authentication error: " + e.getMessage());
        }
        return false;
    }

    public static boolean saveMessage(String sender, String receiver, String message) {
        String sql = "INSERT INTO messages (sender, receiver, message, timestamp) VALUES (?, ?, ?, NOW())";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sender);
            pstmt.setString(2, receiver);
            pstmt.setString(3, message);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DBHandler] Error saving message: " + e.getMessage());
            return false;
        }
    }

    public static class HistoryRecord {
        public final String timestamp;
        public final String sender;
        public final String receiver;
        public final String message;

        public HistoryRecord(String timestamp, String sender, String receiver, String message) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.receiver = receiver;
            this.message = message;
        }
    }

    /**
     * Retrieves historical messages partitioned by department and authority:
     * - Main Notice Board messages (receiver = 'MAIN' or 'ALL')
     * - The user's specific Department Notice Board messages (receiver = 'DEPT:<userDepartment>')
     * - 1-to-1 Confidential Faculty Memos where the user was sender or recipient
     * Notices belonging to other departments are excluded.
     */
    public static List<HistoryRecord> getChatHistory(String username, String department) {
        List<HistoryRecord> history = new ArrayList<>();
        String deptTarget = "DEPT:" + (department != null ? department.trim() : "");

        String sql = "SELECT sender, receiver, message, " +
                     "DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:%s') AS formatted_time " +
                     "FROM messages " +
                     "WHERE receiver = 'MAIN' OR receiver = 'ALL' " +
                     "   OR receiver = ? " +
                     "   OR LOWER(sender) = LOWER(?) OR LOWER(receiver) = LOWER(?) " +
                     "ORDER BY timestamp ASC LIMIT 150";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, deptTarget);
            pstmt.setString(2, username);
            pstmt.setString(3, username);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new HistoryRecord(
                        rs.getString("formatted_time"),
                        rs.getString("sender"),
                        rs.getString("receiver"),
                        rs.getString("message")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[DBHandler] Error fetching history: " + e.getMessage());
        }
        return history;
    }
}
