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
 * Demonstrates:
 *  - Socket creation and connection management
 *  - Separate background worker thread for non-blocking stream listening
 *  - Protocol message dispatching
 *  - Staff profile awareness (Department, Designation)
 *  - Base64 encoding/decoding for academic document transfer
 *  - Observer/Listener pattern to notify GUI on the Swing EDT
 */
public class ChatClient {

    public interface ChatEventListener {
        void onLoginSuccess(String username, String department, String designation);
        void onLoginFailure(String reason);
        void onRegisterSuccess(String message);
        void onRegisterFailure(String reason);
        void onBroadcastReceived(String timestamp, String sender, String message);
        void onPrivateReceived(String timestamp, String sender, String message);
        void onPrivateSent(String timestamp, String recipient, String message);
        void onSystemMessage(String timestamp, String message);
        void onUserListUpdated(List<String> rawUsers);
        void onHistoryMessage(String timestamp, String sender, String receiver, String message);
        void onHistoryDone();
        void onFileBroadcastReceived(String timestamp, String sender, String fileName, byte[] fileData);
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

    /**
     * Connects to the server and launches the background reader thread.
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (isConnected()) {
            return;
        }

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        isRunning = true;

        // Background worker thread to listen for incoming messages from the server
        listenerThread = new Thread(this::listenLoop, "ChatClient-ListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    /**
     * Continuous loop reading protocol messages from the server socket.
     */
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

    /**
     * Parses incoming protocol commands from the server.
     */
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
                    } else {
                        currentDepartment = "Computer Science & AI";
                        currentDesignation = "Assistant Professor";
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

            case "MSG":
                // Format: MSG|timestamp|sender|message
                if (parts.length >= 4 && listener != null) {
                    listener.onBroadcastReceived(parts[1], parts[2], parts[3]);
                }
                break;

            case "PRIV_MSG":
                // Format: PRIV_MSG|timestamp|sender|message
                if (parts.length >= 4 && listener != null) {
                    listener.onPrivateReceived(parts[1], parts[2], parts[3]);
                }
                break;

            case "PRIV_SENT":
                // Format: PRIV_SENT|timestamp|recipient|message
                if (parts.length >= 4 && listener != null) {
                    listener.onPrivateSent(parts[1], parts[2], parts[3]);
                }
                break;

            case "SYS_MSG":
                // Format: SYS_MSG|timestamp|text
                if (parts.length >= 3 && listener != null) {
                    listener.onSystemMessage(parts[1], parts[2]);
                }
                break;

            case "USER_LIST":
                // Format: USER_LIST|user1:dept:desig,user2:dept:desig,...
                if (listener != null) {
                    List<String> users = new ArrayList<>();
                    if (parts.length >= 2 && !parts[1].trim().isEmpty()) {
                        users.addAll(Arrays.asList(parts[1].split(",")));
                    }
                    listener.onUserListUpdated(users);
                }
                break;

            case "HIST_MSG":
                // Format: HIST_MSG|timestamp|sender|receiver|message
                if (parts.length >= 5 && listener != null) {
                    listener.onHistoryMessage(parts[1], parts[2], parts[3], parts[4]);
                }
                break;

            case "HIST_DONE":
                if (listener != null) {
                    listener.onHistoryDone();
                }
                break;

            case "FILE_MSG": {
                // Format: FILE_MSG|timestamp|sender|fileName|base64
                if (parts.length >= 5 && listener != null) {
                    try {
                        byte[] data = Base64.getDecoder().decode(parts[4]);
                        listener.onFileBroadcastReceived(parts[1], parts[2], parts[3], data);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                break;
            }

            case "FILE_PRIV_MSG": {
                // Format: FILE_PRIV_MSG|timestamp|sender|fileName|base64
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
                // Format: FILE_PRIV_SENT|timestamp|recipient|fileName|base64
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

    public synchronized void sendRegister(String username, String password) {
        sendRegister(username, password, "Computer Science & AI", "Assistant Professor");
    }

    public synchronized void sendBroadcast(String message) {
        sendMessage("BROADCAST|" + message);
    }

    public synchronized void sendPrivate(String recipient, String message) {
        sendMessage("PRIVATE|" + recipient + "|" + message);
    }

    public synchronized void sendFileBroadcast(String fileName, byte[] data) {
        String base64 = Base64.getEncoder().encodeToString(data);
        sendMessage("FILE_BROADCAST|" + fileName + "|" + base64);
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
