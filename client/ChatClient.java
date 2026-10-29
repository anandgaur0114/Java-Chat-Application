package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * ChatClient handles TCP socket communication with the ChatServer
 * for JECRC StaffNet (College Staff Information & Circular System).
 * Handles:
 *  - Main Campus Notice Board (Principal & HOD broadcasts)
 *  - Department-specific Notice Board (isolated by department)
 *  - Confidential Faculty Memos (1-to-1)
 *  - Academic file transmission (Base64 over TCP stream)
 */
public class ChatClient {

    public interface ChatEventListener {
        void onLoginSuccess(String username, String department, String designation);
        void onLoginFailure(String reason);
        void onRegisterSuccess(String message);
        void onRegisterFailure(String reason);

        void onMainNoticeReceived(String timestamp, String sender, String message);
        void onDeptNoticeReceived(String timestamp, String sender, String department, String message);
        void onPrivateReceived(String timestamp, String sender, String message);
        void onPrivateSent(String timestamp, String recipient, String message);
        void onSystemMessage(String timestamp, String message);
        void onUserListUpdated(List<String> rawUsers);

        void onHistoryMainNotice(String timestamp, String sender, String message);
        void onHistoryDeptNotice(String timestamp, String sender, String department, String message);
        void onHistoryPrivate(String timestamp, String sender, String receiver, String message);
        void onHistoryDone();

        void onFileMainReceived(String timestamp, String sender, String fileName, byte[] fileData);
        void onFileDeptReceived(String timestamp, String sender, String department, String fileName, byte[] fileData);
        void onFilePrivateReceived(String timestamp, String sender, String fileName, byte[] fileData);
        void onFilePrivateSent(String timestamp, String recipient, String fileName, byte[] fileData);

        void onDisconnected(String reason);
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private ChatEventListener listener;
    private volatile boolean isRunning = false;
    private String currentUsername;
    private String currentDepartment = "Computer Science & AI";
    private String currentDesignation = "Assistant Professor";

    public void setListener(ChatEventListener listener) {
        this.listener = listener;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentDepartment() {
        return currentDepartment;
    }

    public String getCurrentDesignation() {
        return currentDesignation;
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public synchronized void connect(String host, int port) throws IOException {
        if (isConnected()) {
            return;
        }

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        isRunning = true;

        listenerThread = new Thread(this::listenLoop, "ChatClient-ListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listenLoop() {
        try {
            String line;
            while (isRunning && (line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                processServerMessage(line);
            }
        } catch (IOException e) {
            if (isRunning) {
                notifyDisconnected("Connection to Campus Server lost: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    private void processServerMessage(String line) {
        String[] parts = line.split("\\|", 5);
        String command = parts[0];

        switch (command) {
            case "LOGIN_SUCCESS":
                if (parts.length >= 2) {
                    currentUsername = parts[1];
                    if (parts.length >= 4) {
                        currentDepartment = parts[2];
                        currentDesignation = parts[3];
                    }
                    if (listener != null) {
                        listener.onLoginSuccess(currentUsername, currentDepartment, currentDesignation);
                    }
                }
                break;

            case "LOGIN_FAIL":
                if (listener != null) {
                    listener.onLoginFailure(parts.length >= 2 ? parts[1] : "Login failed.");
                }
                break;

            case "REG_SUCCESS":
                if (listener != null) {
                    listener.onRegisterSuccess(parts.length >= 2 ? parts[1] : "Staff registration successful!");
                }
                break;

            case "REG_FAIL":
                if (listener != null) {
                    listener.onRegisterFailure(parts.length >= 2 ? parts[1] : "Staff registration failed.");
                }
                break;

            case "MSG_MAIN":
                // Format: MSG_MAIN|timestamp|sender|message
                if (parts.length >= 4 && listener != null) {
                    listener.onMainNoticeReceived(parts[1], parts[2], parts[3]);
                }
                break;

            case "MSG_DEPT":
                // Format: MSG_DEPT|timestamp|sender|department|message
                if (parts.length >= 5 && listener != null) {
                    listener.onDeptNoticeReceived(parts[1], parts[2], parts[3], parts[4]);
                }
                break;

            case "PRIV_MSG":
                if (parts.length >= 4 && listener != null) {
                    listener.onPrivateReceived(parts[1], parts[2], parts[3]);
                }
                break;

            case "PRIV_SENT":
                if (parts.length >= 4 && listener != null) {
                    listener.onPrivateSent(parts[1], parts[2], parts[3]);
                }
                break;

            case "SYS_MSG":
                if (parts.length >= 3 && listener != null) {
                    listener.onSystemMessage(parts[1], parts[2]);
                }
                break;

            case "USER_LIST":
                if (listener != null) {
                    List<String> users = new ArrayList<>();
                    if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                        users.addAll(Arrays.asList(parts[1].split(",")));
                    }
                    listener.onUserListUpdated(users);
                }
                break;

            case "HIST_MAIN":
                // Format: HIST_MAIN|timestamp|sender|message
                if (parts.length >= 4 && listener != null) {
                    listener.onHistoryMainNotice(parts[1], parts[2], parts[3]);
                }
                break;

            case "HIST_DEPT":
                // Format: HIST_DEPT|timestamp|sender|department|message
                if (parts.length >= 5 && listener != null) {
                    listener.onHistoryDeptNotice(parts[1], parts[2], parts[3], parts[4]);
                }
                break;

            case "HIST_PRIV":
                // Format: HIST_PRIV|timestamp|sender|receiver|message
                if (parts.length >= 5 && listener != null) {
                    listener.onHistoryPrivate(parts[1], parts[2], parts[3], parts[4]);
                }
                break;

            case "HIST_DONE":
                if (listener != null) {
                    listener.onHistoryDone();
                }
                break;

            case "FILE_MSG_MAIN": {
                // Format: FILE_MSG_MAIN|timestamp|sender|fileName|base64
                if (parts.length >= 5 && listener != null) {
                    try {
                        byte[] data = Base64.getDecoder().decode(parts[4]);
                        listener.onFileMainReceived(parts[1], parts[2], parts[3], data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }

            case "FILE_MSG_DEPT": {
                // Format: FILE_MSG_DEPT|timestamp|sender|dept|fileName|base64
                // Need split 6 for this:
                String[] fParts = line.split("\\|", 6);
                if (fParts.length >= 6 && listener != null) {
                    try {
                        byte[] data = Base64.getDecoder().decode(fParts[5]);
                        listener.onFileDeptReceived(fParts[1], fParts[2], fParts[3], fParts[4], data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }

            case "FILE_PRIV_MSG": {
                if (parts.length >= 5 && listener != null) {
                    try {
                        byte[] data = Base64.getDecoder().decode(parts[4]);
                        listener.onFilePrivateReceived(parts[1], parts[2], parts[3], data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }

            case "FILE_PRIV_SENT": {
                if (parts.length >= 5 && listener != null) {
                    try {
                        byte[] data = Base64.getDecoder().decode(parts[4]);
                        listener.onFilePrivateSent(parts[1], parts[2], parts[3], data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }

            default:
                break;
        }
    }

    public synchronized void sendLogin(String username, String password) {
        sendMessage("LOGIN|" + username + "|" + password);
    }

    public synchronized void sendRegister(String username, String password, String department, String designation) {
        sendMessage("REGISTER|" + username + "|" + password + "|" + department + "|" + designation);
    }

    public synchronized void sendBroadcastMain(String message) {
        sendMessage("BROADCAST_MAIN|" + message);
    }

    public synchronized void sendBroadcastDept(String message) {
        sendMessage("BROADCAST_DEPT|" + message);
    }

    public synchronized void sendPrivate(String recipient, String message) {
        sendMessage("PRIVATE|" + recipient + "|" + message);
    }

    public synchronized void sendFileBroadcastMain(String fileName, byte[] data) {
        String base64 = Base64.getEncoder().encodeToString(data);
        sendMessage("FILE_BROADCAST_MAIN|" + fileName + "|" + base64);
    }

    public synchronized void sendFileBroadcastDept(String fileName, byte[] data) {
        String base64 = Base64.getEncoder().encodeToString(data);
        sendMessage("FILE_BROADCAST_DEPT|" + fileName + "|" + base64);
    }

    public synchronized void sendFilePrivate(String recipient, String fileName, byte[] data) {
        String base64 = Base64.getEncoder().encodeToString(data);
        sendMessage("FILE_PRIVATE|" + recipient + "|" + fileName + "|" + base64);
    }

    public synchronized void sendLogout() {
        sendMessage("LOGOUT");
        disconnect();
    }

    private void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    public synchronized void disconnect() {
        isRunning = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }

    private void notifyDisconnected(String reason) {
        if (listener != null) {
            listener.onDisconnected(reason);
        }
    }
}
