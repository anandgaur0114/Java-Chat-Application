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
 * ChatServer is the central multi-threaded server application.
 * Demonstrates:
 *  - java.net.ServerSocket listening on TCP port
 *  - java.util.concurrent.ExecutorService thread pooling
 *  - java.util.concurrent.ConcurrentHashMap for thread-safe client tracking
 *  - Centralized message routing (Broadcast and 1-to-1 Private)
 */
public class ChatServer {

    public static final int PORT = 12345;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Thread-safe collection of currently online clients: <Username, ClientHandler>
    private static final ConcurrentHashMap<String, ClientHandler> onlineClients = new ConcurrentHashMap<>();

    // Cached thread pool that dynamically allocates threads for connected clients
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("       CORE JAVA CHAT SERVER (JDK 25)            ");
        System.out.println("=================================================");

        // 1. Verify Database Connectivity
        System.out.println("[Server] Testing database connection via JDBC...");
        if (DBHandler.testConnection()) {
            System.out.println("[Server] Database connection OK! (MySQL 8.0 / Connector/J 9.7.0)");
        } else {
            System.err.println("[Server] WARNING: Could not connect to MySQL! Please verify that:");
            System.err.println("         1. MySQL Server service is running.");
            System.err.println("         2. Database 'chatapp_db' was created with schema.sql.");
            System.err.println("         3. Credentials in DBHandler.java match your MySQL configuration.");
        }

        // 2. Start TCP ServerSocket
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[Server] Listening for client connections on TCP port: " + PORT);
            System.out.println("[Server] Ready to accept incoming clients...\n");

            // Server loop: continuously accepts incoming connections
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New connection from: " + clientSocket.getRemoteSocketAddress());

                // Assign each client to a worker thread from the thread pool
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

    /**
     * Checks if a user is currently logged in.
     */
    public static boolean isUserOnline(String username) {
        return onlineClients.containsKey(username.toLowerCase());
    }

    /**
     * Registers an authenticated client into active sessions.
     */
    public static synchronized void registerClient(String username, ClientHandler handler) {
        onlineClients.put(username.toLowerCase(), handler);
        System.out.println("[Server] User logged in: " + username + " (Total online: " + onlineClients.size() + ")");

        // Notify all clients of updated online users
        broadcastUserList();

        // Broadcast system notification
        broadcastSystemMessage("User '" + username + "' has joined the chat.");
    }

    /**
     * Unregisters a client on disconnect or logout.
     */
    public static synchronized void unregisterClient(String username) {
        if (username != null && onlineClients.remove(username.toLowerCase()) != null) {
            System.out.println("[Server] User logged out: " + username + " (Total online: " + onlineClients.size() + ")");

            // Broadcast updated user list
            broadcastUserList();

            // Broadcast system notification
            broadcastSystemMessage("User '" + username + "' has left the chat.");
        }
    }

    /**
     * Broadcasts a group message to all online clients and saves it to MySQL.
     */
    public static void broadcastMessage(String sender, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);

        // 1. Persist in MySQL database (receiver = 'ALL')
        DBHandler.saveMessage(sender, "ALL", message);

        // 2. Send over socket to all active clients
        String protocolMsg = "MSG|" + timestamp + "|" + sender + "|" + message;
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(protocolMsg);
        }
    }

    /**
     * Sends a 1-to-1 private message.
     * Persists in DB and routes to recipient and sender.
     * @return true if recipient is online, false otherwise.
     */
    public static boolean sendPrivateMessage(String sender, String receiver, String message) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);

        // 1. Persist in MySQL database
        DBHandler.saveMessage(sender, receiver, message);

        // 2. Locate recipient in online client registry
        ClientHandler recipientHandler = onlineClients.get(receiver.toLowerCase());
        boolean isRecipientOnline = (recipientHandler != null);

        if (isRecipientOnline) {
            recipientHandler.sendMessage("PRIV_MSG|" + timestamp + "|" + sender + "|" + message);
        }

        // Echo back to sender for confirmation in their chat window
        ClientHandler senderHandler = onlineClients.get(sender.toLowerCase());
        if (senderHandler != null) {
            senderHandler.sendMessage("PRIV_SENT|" + timestamp + "|" + receiver + "|" + message);
            if (!isRecipientOnline) {
                senderHandler.sendMessage("SYS_MSG|" + timestamp + "|[INFO] " + receiver + " is offline, but will see your message in history.");
            }
        }

        return isRecipientOnline;
    }

    /**
     * Broadcasts the current list of online users to all clients.
     */
    public static void broadcastUserList() {
        StringBuilder sb = new StringBuilder("USER_LIST|");
        boolean first = true;
        for (ClientHandler client : onlineClients.values()) {
            if (!first) {
                sb.append(",");
            }
            sb.append(client.getUsername());
            first = false;
        }
        String listMessage = sb.toString();

        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(listMessage);
        }
    }

    /**
     * Broadcasts a server-level notification (e.g. joins, leaves).
     */
    public static void broadcastSystemMessage(String text) {
        String timestamp = LocalTime.now().format(TIME_FORMATTER);
        String msg = "SYS_MSG|" + timestamp + "|" + text;
        for (ClientHandler client : onlineClients.values()) {
            client.sendMessage(msg);
        }
    }
}
