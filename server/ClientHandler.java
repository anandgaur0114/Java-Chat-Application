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
 * Enforces:
 *  - Main Notice Board permission check: Only Principal and HODs may broadcast campus-wide.
 *  - Department isolation: Department notices are routed strictly to colleagues of the same department.
 *  - 1-to-1 Confidential Faculty Memos.
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private String department = "Computer Science & AI";
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
                        sendChatHistory(user, profile.department);
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

    private void sendChatHistory(String user, String dept) {
        List<DBHandler.HistoryRecord> history = DBHandler.getChatHistory(user, dept);
        for (DBHandler.HistoryRecord rec : history) {
            if ("MAIN".equalsIgnoreCase(rec.receiver) || "ALL".equalsIgnoreCase(rec.receiver)) {
                sendMessage("HIST_MAIN|" + rec.timestamp + "|" + rec.sender + "|" + rec.message);
            } else if (rec.receiver.startsWith("DEPT:")) {
                String dName = rec.receiver.substring(5);
                sendMessage("HIST_DEPT|" + rec.timestamp + "|" + rec.sender + "|" + dName + "|" + rec.message);
            } else {
                sendMessage("HIST_PRIV|" + rec.timestamp + "|" + rec.sender + "|" + rec.receiver + "|" + rec.message);
            }
        }
        sendMessage("HIST_DONE");
    }

    private void handleChatProtocol(String commandLine) {
        // --- 1. File Attachments ---
        if (commandLine.startsWith("FILE_BROADCAST_MAIN|")) {
            String[] fParts = commandLine.split("\\|", 3);
            if (fParts.length >= 3) {
                if (!DBHandler.isAuthority(designation)) {
                    sendMessage("SYS_MSG|[ERROR] Permission Denied: Only Principal and HODs can attach documents on the Main Notice Board.");
                    return;
                }
                ChatServer.broadcastMainFile(username, fParts[1], fParts[2]);
            }
            return;
        } else if (commandLine.startsWith("FILE_BROADCAST_DEPT|")) {
            String[] fParts = commandLine.split("\\|", 3);
            if (fParts.length >= 3) {
                ChatServer.broadcastDeptFile(username, department, fParts[1], fParts[2]);
            }
            return;
        } else if (commandLine.startsWith("FILE_PRIVATE|")) {
            String[] fParts = commandLine.split("\\|", 4);
            if (fParts.length >= 4) {
                ChatServer.sendPrivateFile(username, fParts[1].trim(), fParts[2], fParts[3]);
            }
            return;
        }

        // --- 2. Notice Board & Memo Messages ---
        String[] parts = commandLine.split("\\|", 3);
        String action = parts[0];

        switch (action) {
            case "BROADCAST_MAIN":
                if (parts.length >= 2) {
                    if (!DBHandler.isAuthority(designation)) {
                        sendMessage("SYS_MSG|[ERROR] Permission Denied: Only Principal and HODs can broadcast on the Main Notice Board.");
                        return;
                    }
                    ChatServer.broadcastMainMessage(username, parts[1]);
                }
                break;

            case "BROADCAST_DEPT":
                if (parts.length >= 2) {
                    ChatServer.broadcastDeptMessage(username, department, parts[1]);
                }
                break;

            case "BROADCAST":
                // Fallback: If authority, broadcast to Main; otherwise broadcast to department
                if (parts.length >= 2) {
                    if (DBHandler.isAuthority(designation)) {
                        ChatServer.broadcastMainMessage(username, parts[1]);
                    } else {
                        ChatServer.broadcastDeptMessage(username, department, parts[1]);
                    }
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
