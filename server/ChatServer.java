package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ChatServer is the central multi-threaded server application
 * for JECRC StaffNet (College Staff Information & Circular Distribution System).
 * Demonstrates:
 *  - java.net.ServerSocket listening on TCP port
 *  - Authority enforcement (Main Notice Board restricted to Principal & HODs)
 *  - Department-isolated Notice Board broadcasting
 *  - 1-to-1 Confidential Faculty Memos
 *  - Academic file and document transfer dispatching
 */
public class ChatServer {

    public static final int PORT = 12345;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Thread-safe collection of currently online staff: <Username, ClientHandler>
    private static final ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    // Cached thread pool that dynamically allocates threads for connected staff workstations
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("   JECRC STAFFNET: CAMPUS INTRANET SERVER (JDK 25)");
        System.out.println("   Faculty Information & Circular Distribution");
        System.out.println("=================================================");

        // 1. Verify Database Connectivity & Initialize Staff Schema
        System.out.println("[Server] Connecting to MySQL database via JDBC...");
        if (DBHandler.testConnection()) {
            System.out.println("[Server] Database connection OK! (MySQL 8.0 / Connector/J 9.7.0)");
            DBHandler.initializeStaffSystem();
        } else {
            System.err.println("[Server] WARNING: Could not connect to MySQL! Please verify that:");
            System.err.println("         1. MySQL Server service is running.");
            System.err.println("         2. Database 'chatapp_db' exists (or run schema.sql).");
            System.err.println("         3. Credentials in db.properties match your MySQL root setup.");
        }

        // 2. Start TCP ServerSocket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Campus Intranet Socket listening on TCP Port: " + PORT);
            System.out.println("[Server] Ready to dispatch departmental and campus circulars...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] Incoming staff connection from: " + clientSocket.getRemoteSocketAddress());

                ClientHandler handler = new ClientHandler(clientSocket);
                threadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("[Server] Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            threadPool.shutdown();
        }
    }

    public static boolean isUserOnline(String username) {
        return onlineClients.containsKey(username.toLowerCase());
    }

    public static synchronized void registerClient(String username, ClientHandler handler) {
        onlineClients.put(username.toLowerCase(), handler);
        System.out.println("[Server] Staff member logged in: " + username + " [" + handler.getDesignation() + ", " + handler.getDepartment() + "] (Online staff: " + onlineClients.size() + ")");

        broadcastUserList();
        broadcastSystemMessage("Staff member '" + username + "' (" + handler.getDesignation() + " - " + handler.getDepartment() + ") is now online.");
    }

    public static synchronized void unregisterClient(String username) {
        if (username != null) {
            ClientHandler removed = onlineClients.remove(username.toLowerCase());
            if (removed != null) {
                System.out.println("[Server] Staff member logged out: " + username + " (Online staff: " + onlineClients.size() + ")");
                broadcastUserList();
                broadcastSystemMessage("Staff member '" + username + "' has signed off.");
            }
        }
    }

    /**
     * Broadcasts a notice to the MAIN Campus Notice Board (posted by Principal or HOD).
     * Saved in MySQL as receiver = 'MAIN', dispatched to ALL online staff across all departments.
     */
    public static void broadcastMainMessage(String sender, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        DBHandler.saveMessage(sender, "MAIN", message);

        String protocolMsg = "MSG_MAIN|" + timestamp + "|" + sender + "|" + message;
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(protocolMsg);
        }
    }

    /**
     * Broadcasts a notice to a specific DEPARTMENT Notice Board.
     * Saved in MySQL as receiver = 'DEPT:<department>', dispatched ONLY to faculty of that department.
     */
    public static void broadcastDeptMessage(String sender, String department, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        DBHandler.saveMessage(sender, "DEPT:" + department, message);

        String protocolMsg = "MSG_DEPT|" + timestamp + "|" + sender + "|" + department + "|" + message;
        for (ClientHandler client : onlineClients.values()) {
            if (client.getDepartment().equalsIgnoreCase(department)) {
                client.sendMessage(protocolMsg);
            }
        }
    }

    /**
     * Broadcasts an official document attachment on the MAIN Notice Board.
     */
    public static void broadcastMainFile(String sender, String fileName, String base64Data) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        DBHandler.saveMessage(sender, "MAIN", "[FILE_ATTACHMENT: " + fileName + "]");

        String protocolMsg = "FILE_MSG_MAIN|" + timestamp + "|" + sender + "|" + fileName + "|" + base64Data;
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(protocolMsg);
        }
    }

    /**
     * Broadcasts an academic document attachment to a specific DEPARTMENT Notice Board.
     */
    public static void broadcastDeptFile(String sender, String department, String fileName, String base64Data) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        DBHandler.saveMessage(sender, "DEPT:" + department, "[FILE_ATTACHMENT: " + fileName + "]");

        String protocolMsg = "FILE_MSG_DEPT|" + timestamp + "|" + sender + "|" + department + "|" + fileName + "|" + base64Data;
        for (ClientHandler client : onlineClients.values()) {
            if (client.getDepartment().equalsIgnoreCase(department)) {
                client.sendMessage(protocolMsg);
            }
        }
    }

    /**
     * Sends a 1-to-1 confidential staff memo between two faculty members.
     */
    public static boolean sendPrivateMessage(String sender, String receiver, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        DBHandler.saveMessage(sender, receiver, message);

        ClientHandler recipientHandler = onlineClients.get(receiver.toLowerCase());
        boolean isRecipientOnline = (recipientHandler != null);

        if (isRecipientOnline) {
            recipientHandler.sendMessage("PRIV_MSG|" + timestamp + "|" + sender + "|" + message);
        }

        ClientHandler senderHandler = onlineClients.get(sender.toLowerCase());
        if (senderHandler != null) {
            senderHandler.sendMessage("PRIV_SENT|" + timestamp + "|" + receiver + "|" + message);
            if (!isRecipientOnline) {
                senderHandler.sendMessage("SYS_MSG|" + timestamp + "|[INFO] " + receiver + " is currently offline. Your memo was saved to MySQL history.");
            }
        }

        return isRecipientOnline;
    }

    /**
     * Sends a confidential file attachment directly to a specific staff member.
     */
    public static boolean sendPrivateFile(String sender, String receiver, String fileName, String base64Data) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        DBHandler.saveMessage(sender, receiver, "[FILE_ATTACHMENT: " + fileName + "]");

        ClientHandler recipientHandler = onlineClients.get(receiver.toLowerCase());
        boolean isRecipientOnline = (recipientHandler != null);

        if (isRecipientOnline) {
            recipientHandler.sendMessage("FILE_PRIV_MSG|" + timestamp + "|" + sender + "|" + fileName + "|" + base64Data);
        }

        ClientHandler senderHandler = onlineClients.get(sender.toLowerCase());
        if (senderHandler != null) {
            senderHandler.sendMessage("FILE_PRIV_SENT|" + timestamp + "|" + receiver + "|" + fileName + "|" + base64Data);
            if (!isRecipientOnline) {
                senderHandler.sendMessage("SYS_MSG|" + timestamp + "|[INFO] " + receiver + " is offline. File transfer was recorded.");
            }
        }

        return isRecipientOnline;
    }

    public static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USER_LIST|");
        boolean first = true;
        for (ClientHandler client : onlineClients.values()) {
            if (!first) {
                sb.append(",");
            }
            String u = client.getUsername().replace(":", "-").replace(",", " ");
            String dep = client.getDepartment().replace(":", "-").replace(",", " ");
            String des = client.getDesignation().replace(":", "-").replace(",", " ");
            sb.append(u).append(":").append(dep).append(":").append(des);
            first = false;
        }
        String listMessage = sb.toString();

        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(listMessage);
        }
    }

    public static void broadcastSystemMessage(String text) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String msg = "SYS_MSG|" + timestamp + "|" + text;
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(msg);
        }
    }
}
