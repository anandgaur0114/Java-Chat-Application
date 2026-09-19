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
import java.util.List;

/**
 * ChatClient handles TCP socket communication with the ChatServer.
 * Demonstrates:
 *  - Socket creation and connection management
 *  - Separate background thread for continuous non-blocking socket reading
 *  - Protocol message dispatching
 *  - Observer/Listener pattern to notify GUI of events on the Swing EDT
 */
public class ChatClient {

    public interface ChatEventListener {
        void onLoginSuccess(String username);
        void onLoginFailure(String reason);
        void onRegisterSuccess(String message);
        void onRegisterFailure(String reason);
        void onBroadcastReceived(String timestamp, String sender, String message);
        void onPrivateReceived(String timestamp, String sender, String message);
        void onPrivateSent(String timestamp, String recipient, String message);
        void onSystemMessage(String timestamp, String message);
        void onUserListUpdated(List<String> users);
        void onHistoryMessage(String timestamp, String sender, String receiver, String message);
        void onHistoryDone();
        void onDisconnected(String reason);
    }

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread listenerThread;
    private ChatEventListener listener;
    private volatile boolean isRunning = false;
    private String currentUsername;

    public void setListener(ChatEventListener listener) {
        this.listener = listener;
    }

    public String getCurrentUsername() {
        return currentUsername;
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
                notifyDisconnected("Connection to server lost: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Parses incoming protocol commands from the server.
     */
    private void processServerMessage(String line) {
        String[] parts = line.split("\\|", 4);
        String command = parts[0];

        switch (command) {
            case "LOGIN_SUCCESS":
                if (parts.length >= 2) {
                    currentUsername = parts[1];
                    if (listener != null) listener.onLoginSuccess(currentUsername);
                }
                break;

            case "LOGIN_FAIL":
                if (listener != null) {
                    listener.onLoginFailure(parts.length >= 2 ? parts[1] : "Login failed.");
                }
                break;

            case "REG_SUCCESS":
                if (listener != null) {
                    listener.onRegisterSuccess(parts.length >= 2 ? parts[1] : "Registration successful!");
                }
                break;

            case "REG_FAIL":
                if (listener != null) {
                    listener.onRegisterFailure(parts.length >= 2 ? parts[1] : "Registration failed.");
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
                // Format: USER_LIST|user1,user2,...
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
                // Notice 5 parts: split limit should be handled
                String[] histParts = line.split("\\|", 5);
                if (histParts.length >= 5 && listener != null) {
                    listener.onHistoryMessage(histParts[1], histParts[2], histParts[3], histParts[4]);
                }
                break;

            case "HIST_DONE":
                if (listener != null) {
                    listener.onHistoryDone();
                }
                break;

            default:
                System.out.println("[Client] Unhandled server command: " + line);
                break;
        }
    }

    public synchronized void sendLogin(String username, String password) {
        if (out != null) {
            out.println("LOGIN|" + username + "|" + password);
        }
    }

    public synchronized void sendRegister(String username, String password) {
        if (out != null) {
            out.println("REGISTER|" + username + "|" + password);
        }
    }

    public synchronized void sendBroadcast(String message) {
        if (out != null) {
            out.println("BROADCAST|" + message);
        }
    }

    public synchronized void sendPrivate(String recipient, String message) {
        if (out != null) {
            out.println("PRIVATE|" + recipient + "|" + message);
        }
    }

    public synchronized void disconnect() {
        if (!isRunning && (socket == null || socket.isClosed())) {
            return;
        }
        isRunning = false;
        try {
            if (out != null) {
                out.println("LOGOUT");
                out.flush();
            }
        } catch (Exception ignored) {}

        try {
            if (in != null) in.close();
        } catch (IOException ignored) {}
        if (out != null) out.close();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        notifyDisconnected("Disconnected from server.");
    }

    private void notifyDisconnected(String reason) {
        if (listener != null) {
            listener.onDisconnected(reason);
        }
    }
}
