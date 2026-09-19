package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ClientHandler manages communication with an individual connected client.
 * Demonstrates:
 *  - java.lang.Runnable for multi-threaded concurrency
 *  - Socket I/O streaming using BufferedReader and PrintWriter (UTF-8)
 *  - Protocol parsing and command routing
 *  - State tracking (unauthenticated vs authenticated)
 *  - Clean disconnection handling
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private boolean isAuthenticated = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return username;
    }

    @Override
    public void run() {
        try {
            // Set up character-based I/O streams with UTF-8 encoding
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String clientLine;
            // Read lines until client disconnects or socket closes
            while ((clientLine = in.readLine()) != null) {
                clientLine = clientLine.trim();
                if (clientLine.isEmpty()) {
                    continue;
                }

                if (!isAuthenticated) {
                    handleAuthProtocol(clientLine);
                } else {
                    handleChatProtocol(clientLine);
                }
            }
        } catch (IOException e) {
            // Socket reset or unexpected client drop
            System.out.println("[ClientHandler] Connection terminated for " + (username != null ? username : "unauthenticated client"));
        } finally {
            cleanup();
        }
    }

    /**
     * Processes protocol commands before a client is authenticated.
     */
    private void handleAuthProtocol(String commandLine) {
        String[] parts = commandLine.split("\\|", 3);
        String action = parts[0];

        switch (action) {
            case "LOGIN":
                if (parts.length >= 3) {
                    String user = parts[1].trim();
                    String pass = parts[2].trim();

                    // Check if already online
                    if (ChatServer.isUserOnline(user)) {
                        sendMessage("LOGIN_FAIL|User '" + user + "' is already logged in elsewhere.");
                        return;
                    }

                    // Authenticate with MySQL database
                    if (DBHandler.authenticateUser(user, pass)) {
                        this.username = user;
                        this.isAuthenticated = true;
                        sendMessage("LOGIN_SUCCESS|" + user);

                        // Register with the server
                        ChatServer.registerClient(user, this);

                        // Send historical messages to client
                        sendChatHistory(user);
                    } else {
                        sendMessage("LOGIN_FAIL|Invalid username or password.");
                    }
                } else {
                    sendMessage("LOGIN_FAIL|Malformed LOGIN request.");
                }
                break;

            case "REGISTER":
                if (parts.length >= 3) {
                    String user = parts[1].trim();
                    String pass = parts[2].trim();

                    if (user.isEmpty() || pass.isEmpty()) {
                        sendMessage("REG_FAIL|Username and password cannot be empty.");
                        return;
                    }

                    if (DBHandler.registerUser(user, pass)) {
                        sendMessage("REG_SUCCESS|Registration successful! You can now log in.");
                    } else {
                        sendMessage("REG_FAIL|Username already taken or database unavailable.");
                    }
                } else {
                    sendMessage("REG_FAIL|Malformed REGISTER request.");
                }
                break;

            default:
                sendMessage("ERROR|Please authenticate with LOGIN or REGISTER first.");
                break;
        }
    }

    /**
     * Streams previous chat history from MySQL to the newly logged-in user.
     */
    private void sendChatHistory(String user) {
        List<DBHandler.HistoryRecord> history = DBHandler.getChatHistory(user);
        for (DBHandler.HistoryRecord rec : history) {
            sendMessage("HIST_MSG|" + rec.timestamp + "|" + rec.sender + "|" + rec.receiver + "|" + rec.message);
        }
        sendMessage("HIST_DONE");
    }

    /**
     * Processes messages and chat commands for an authenticated user.
     */
    private void handleChatProtocol(String commandLine) {
        String[] parts = commandLine.split("\\|", 3);
        String action = parts[0];

        switch (action) {
            case "BROADCAST":
                if (parts.length >= 2) {
                    String text = parts[1];
                    ChatServer.broadcastMessage(username, text);
                }
                break;

            case "PRIVATE":
                if (parts.length >= 3) {
                    String recipient = parts[1].trim();
                    String text = parts[2];
                    if (recipient.equalsIgnoreCase(username)) {
                        sendMessage("SYS_MSG|SYSTEM|You cannot send private messages to yourself.");
                    } else {
                        ChatServer.sendPrivateMessage(username, recipient, text);
                    }
                }
                break;

            case "LOGOUT":
                cleanup();
                break;

            default:
                sendMessage("ERROR|Unknown command: " + action);
                break;
        }
    }

    /**
     * Sends a raw line formatted in protocol syntax to this client.
     */
    public synchronized void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    /**
     * Closes socket resources cleanly and unregisters client from server.
     */
    private void cleanup() {
        if (isAuthenticated && username != null) {
            ChatServer.unregisterClient(username);
            isAuthenticated = false;
        }
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        if (out != null) out.close();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }
}
