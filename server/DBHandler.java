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
 *  - Staff Profile schema migration (Department, Designation, Roles)
 *  - Pre-seeding institutional staff accounts and official circular notices
 *  - PreparedStatements to prevent SQL Injection
 *  - SHA-256 Hashing for secure password storage
 *  - Query execution, transactions, and ResultSet processing
 */
public class DBHandler {

    // Default connection parameters
    private static String dbUrl = "jdbc:mysql://localhost:3306/chatapp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static String dbUser = "root";
    private static String dbPassword = "root";

    // Static initializer: load driver, optional db.properties, and initialize schema
    static {
        // 1. Load configuration from db.properties if present
        loadConfig();

        // 2. Load MySQL Connector/J driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBHandler] MySQL JDBC Driver not found in classpath! Check lib/ folder.");
            e.printStackTrace();
        }
    }

    /**
     * Attempts to load DB credentials from 'db.properties'.
     * Falls back to defaults (root/root) if file is missing.
     */
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

    /**
     * Establishes and returns a new database Connection.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    /**
     * Tests the database connection and prints diagnostic information.
     */
    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DBHandler] Connection failed: " + e.getMessage());
            System.err.println("[DBHandler] Check URL: " + dbUrl + " | User: " + dbUser);
            System.err.println("[DBHandler] You can configure credentials in db.properties.");
            return false;
        }
    }

    /**
     * Auto-migrates schema to ensure staff columns exist (Department, Designation, Role)
     * and seeds default faculty accounts and official notices.
     */
    public static void initializeStaffSystem() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            // 1. Create tables if not exist
            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "username VARCHAR(50) NOT NULL UNIQUE, " +
                "password_hash VARCHAR(255) NOT NULL, " +
                "department VARCHAR(100) DEFAULT 'Computer Science & Engineering', " +
                "designation VARCHAR(100) DEFAULT 'Assistant Professor', " +
                "role VARCHAR(30) DEFAULT 'FACULTY', " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );

            stmt.executeUpdate(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "sender VARCHAR(50) NOT NULL, " +
                "receiver VARCHAR(50) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_sender (sender), " +
                "INDEX idx_receiver (receiver), " +
                "INDEX idx_timestamp (timestamp))"
            );

            // 2. Safe alteration in case table existed previously without extra columns
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN department VARCHAR(100) DEFAULT 'Computer Science & AI'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN designation VARCHAR(100) DEFAULT 'Assistant Professor'"); } catch (SQLException ignored) {}
            try { stmt.executeUpdate("ALTER TABLE users ADD COLUMN role VARCHAR(30) DEFAULT 'FACULTY'"); } catch (SQLException ignored) {}

            // 3. Pre-seed default faculty & staff accounts (password: admin123)
            String defaultPassHash = hashPassword("admin123");
            String seedUsersSql = 
                "INSERT IGNORE INTO users (username, password_hash, department, designation, role) VALUES " +
                "('mona.nagar', '" + defaultPassHash + "', 'Computer Science & AI', 'Assistant Professor & Guide', 'HOD')," +
                "('abhinav.batra', '" + defaultPassHash + "', 'Computer Science & AI', 'Assistant Professor & Exam Cell', 'FACULTY')," +
                "('anand.gaur', '" + defaultPassHash + "', 'AI & Data Science', 'Head of Department (HOD)', 'HOD')," +
                "('amit.kumar', '" + defaultPassHash + "', 'AI & Computing Labs', 'Senior Lab Incharge', 'STAFF')," +
                "('abhishek.sihag', '" + defaultPassHash + "', 'Examination Cell', 'Academic Controller', 'ADMIN')," +
                "('principal.office', '" + defaultPassHash + "', 'Principal Secretariat', 'Executive Administrator', 'ADMIN')";
            try {
                stmt.executeUpdate(seedUsersSql);
            } catch (SQLException ignored) {}

            // 4. Seed initial official circulars if messages table is empty
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages")) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate(
                        "INSERT INTO messages (sender, receiver, message) VALUES " +
                        "('principal.office', 'ALL', '[CIRCULAR] Welcome to JECRC StaffNet: Official College Staff Information, Notice & Circular Distribution System (Academic Year 2026-27).')," +
                        "('mona.nagar', 'ALL', '[MEETING] Academic Review & Project Progress meeting for 3rd Year CSE (AI) scheduled for Friday at 2:30 PM in Lab 3.')," +
                        "('abhishek.sihag', 'ALL', '[EXAM] Faculty invigilation duty rosters for upcoming Mid-Term examinations are now published. Please verify your slots.')," +
                        "('anand.gaur', 'ALL', '[URGENT] NAAC Accreditation criterion documentation verification tomorrow morning at 10:00 AM. All department files must be updated.')"
                    );
                }
            } catch (SQLException ignored) {}

            System.out.println("[DBHandler] StaffNet schema initialized and staff directory verified.");
        } catch (SQLException e) {
            System.err.println("[DBHandler] Schema initialization notice: " + e.getMessage());
        }
    }

    /**
     * Hashes a plain-text password using the SHA-256 algorithm.
     */
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

    /**
     * Staff profile representation with institutional department, designation, and role.
     */
    public static class StaffProfile {
        public final String username;
        public final String department;
        public final String designation;
        public final String role;

        public StaffProfile(String username, String department, String designation, String role) {
            this.username = username;
            this.department = (department != null && !department.trim().isEmpty()) ? department.trim() : "Computer Science & Engineering";
            this.designation = (designation != null && !designation.trim().isEmpty()) ? designation.trim() : "Assistant Professor";
            this.role = (role != null && !role.trim().isEmpty()) ? role.trim() : "FACULTY";
        }
    }

    /**
     * Retrieves the staff profile including department and designation.
     */
    public static StaffProfile getStaffProfile(String username) {
        if (username == null) {
            return new StaffProfile("Unknown", "Computer Science & AI", "Faculty", "FACULTY");
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
        return new StaffProfile(username, "Computer Science & AI", "Faculty", "FACULTY");
    }

    /**
     * Registers a new staff member into the 'users' table with department and designation.
     */
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

    /**
     * Authenticates a user against the 'users' table.
     */
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

    /**
     * Persists a notice or chat message into the 'messages' table.
     */
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

    /**
     * Represents a single chat history entry.
     */
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
     * Retrieves historical messages relevant to the given user:
     * - All broadcast circulars (receiver = 'ALL')
     * - All confidential staff memos where the user was either sender or receiver
     * Ordered chronologically.
     */
    public static List<HistoryRecord> getChatHistory(String username) {
        List<HistoryRecord> history = new ArrayList<>();
        String sql = "SELECT sender, receiver, message, " +
                     "DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:%s') AS formatted_time " +
                     "FROM messages " +
                     "WHERE receiver = 'ALL' OR LOWER(sender) = LOWER(?) OR LOWER(receiver) = LOWER(?) " +
                     "ORDER BY timestamp ASC LIMIT 100";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            pstmt.setString(2, username);

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
