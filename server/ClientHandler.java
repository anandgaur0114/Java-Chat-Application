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
 * ClientHandler manages communication with an individual connected college staff member.
 * Demonstrates:
 *  - java.lang.Runnable for multi-threaded concurrency
 *  - Socket I/O streaming using BufferedReader and PrintWriter (UTF-8)
 *  - Protocol parsing and command routing
 *  - Staff authentication and role/department tracking
 *  - Clean disconnection handling
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private String department = "Computer Science & Engineering";
    private String designation = "Assistant Professor";
    private boolean isAuthenticated = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public String getUsername() {
        return username;
    }

    public String getDepartment() {
        return department;
    }

    public String getDesignation() {
        return designation;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String clientLine;
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
            System.out.println("[ClientHandler] Connection closed for " + (username != null ? username : "unauthenticated client"));
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

                    if (ChatServer.isUserOnline(user)) {
                        sendMessage("LOGIN_FAIL|Staff user '" + user + "' is already logged in elsewhere on campus.");
                        return;
                    }

                    if (DBHandler.authenticateUser(user, pass)) {
                        DBHandler.StaffProfile profile = DBHandler.getStaffProfile(user);
                        this.username = profile.username;
                        this.department = profile.department;
                        this.designation = profile.designation;
                        this.isAuthenticated = true;

                        sendMessage("LOGIN_SUCCESS|" + user + "|" + profile.department + "|" + profile.designation);
                        ChatServer.registerClient(user, this);
                        sendChatHistory(user);
                    } else {
                        sendMessage("LOGIN_FAIL|Invalid staff credentials. Please check your username and password.");
                    }
                } else {
                    sendMessage("LOGIN_FAIL|Malformed LOGIN request.");
                }
                break;

            case "REGISTER":
                String[] regParts = commandLine.split("\\|", 5);
                if (regParts.length >= 3) {
                    String user = regParts[1].trim();
                    String pass = regParts[2].trim();
                    String dept = (regParts.length >= 4 && !regParts[3].trim().isEmpty()) ? regParts[3].trim() : "Computer Science & AI";
                    String desig = (regParts.length >= 5 && !regParts[4].trim().isEmpty()) ? regParts[4].trim() : "Assistant Professor";

                    if (user.isEmpty() || pass.isEmpty()) {
                        sendMessage("REG_FAIL|Username and password cannot be empty.");
                        return;
                    }

                    if (DBHandler.registerUser(user, pass, dept, desig, "FACULTY")) {
                        sendMessage("REG_SUCCESS|Staff registration successful! You can now log into StaffNet.");
                    } else {
                        sendMessage("REG_FAIL|Staff username already registered or database unavailable.");
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
     * Streams previous notices and confidential memos from MySQL to the logged-in staff member.
     */
    private void sendChatHistory(String user) {
        List<DBHandler.HistoryRecord> history = DBHandler.getChatHistory(user);
        for (DBHandler.HistoryRecord rec : history) {
            sendMessage("HIST_MSG|" + rec.timestamp + "|" + rec.sender + "|" + rec.receiver + "|" + rec.message);
        }
        sendMessage("HIST_DONE");
    }

    /**
     * Processes official notice broadcasts and confidential memos.
     */
    private void handleChatProtocol(String commandLine) {
        if (commandLine.startsWith("FILE_BROADCAST|")) {
            String[] fParts = commandLine.split("\\|", 3);
            if (fParts.length >= 3) {
                String fileName = fParts[1];
                String base64 = fParts[2];
                ChatServer.broadcastFile(username, fileName, base64);
            }
            return;
        } else if (commandLine.startsWith("FILE_PRIVATE|")) {
            String[] fParts = commandLine.split("\\|", 4);
            if (fParts.length >= 4) {
                String recipient = fParts[1].trim();
                String fileName = fParts[2];
                String base64 = fParts[3];
                ChatServer.sendPrivateFile(username, recipient, fileName, base64);
            }
            return;
        }

        String[] parts = commandLine.split("\\|", 3);
        String action = parts[0];

        switch (action) {
            case "BROADCAST":
                if (parts.length >= 2) {
                    String message = parts[1];
                    ChatServer.broadcastMessage(username, message);
                }
                break;

            case "PRIVATE":
                if (parts.length >= 3) {
                    String recipient = parts[1].trim();
                    String message = parts[2];

                    if (recipient.equalsIgnoreCase(username)) {
                        sendMessage("SYS_MSG|Cannot send confidential memo to yourself.");
                        return;
                    }

                    ChatServer.sendPrivateMessage(username, recipient, message);
                } else {
                    sendMessage("SYS_MSG|Invalid confidential memo format.");
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

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    private void cleanup() {
        try {
            if (isAuthenticated && username != null) {
                ChatServer.unregisterClient(username);
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.err.println("[ClientHandler] Error during cleanup: " + e.getMessage());
        }
    }
}
