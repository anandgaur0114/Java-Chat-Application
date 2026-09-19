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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * DBHandler handles all JDBC interactions with the MySQL database.
 * Demonstrates:
 *  - External configuration via Properties file with sensible defaults
 *  - JDBC Driver loading and Connection management
 *  - PreparedStatements to prevent SQL Injection
 *  - SHA-256 Hashing for secure password storage
 *  - Query execution and ResultSet processing
 */
public class DBHandler {

    // Default connection parameters
    private static String dbUrl = "jdbc:mysql://localhost:3306/chatapp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static String dbUser = "root";
    private static String dbPassword = "root";

    // Static initializer: load driver and optional db.properties
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
     * Registers a new user into the 'users' table.
     * @return true if registration succeeded; false if username already taken or DB error.
     */
    public static boolean registerUser(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return false;
        }

        String sql = "INSERT INTO users (username, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username.trim());
            pstmt.setString(2, hashPassword(password));

            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("[DBHandler] Registration error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticates a user against the 'users' table.
     * @return true if credentials are valid, false otherwise.
     */
    public static boolean authenticateUser(String username, String password) {
        if (username == null || password == null) {
            return false;
        }

        String sql = "SELECT password_hash FROM users WHERE username = ?";
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
     * Persists a chat message into the 'messages' table.
     * @param sender sender username
     * @param receiver 'ALL' for broadcast or username for 1-to-1 private chat
     * @param message text content
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
     * - All broadcast messages (receiver = 'ALL')
     * - All private messages where the user was either sender or receiver
     * Ordered chronologically.
     */
    public static List<HistoryRecord> getChatHistory(String username) {
        List<HistoryRecord> history = new ArrayList<>();
        String sql = "SELECT sender, receiver, message, " +
                     "DATE_FORMAT(timestamp, '%Y-%m-%d %H:%i:%s') AS formatted_time " +
                     "FROM messages " +
                     "WHERE receiver = 'ALL' OR sender = ? OR receiver = ? " +
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
