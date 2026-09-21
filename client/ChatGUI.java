package client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * ChatGUI is the graphical Swing user interface for the client.
 * Demonstrates:
 *  - High-DPI enlarged typography for superior readability
 *  - Java Swing layout management (BorderLayout, GridBagLayout, CardLayout)
 *  - Event Dispatch Thread (EDT) safety via SwingUtilities.invokeLater
 *  - JTextPane styled documents for formatted chat display
 *  - JList for real-time online user presence selection
 *  - Seamless transitions between Authentication and Main Chat views
 */
public class ChatGUI extends JFrame implements ChatClient.ChatEventListener {

    // Card identifiers for view swapping
    private static final String VIEW_LOGIN = "LOGIN_VIEW";
    private static final String VIEW_CHAT = "CHAT_VIEW";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);

    // Client networking instance
    private final ChatClient client = new ChatClient();

    // --- Login View Components ---
    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel authStatusLabel;
    private JButton loginBtn;
    private JButton registerBtn;

    // --- Chat View Components ---
    private JLabel currentUserLabel;
    private JTextPane chatPane;
    private StyledDocument doc;
    private DefaultListModel<String> userListModel;
    private JList<String> userJList;
    private JLabel targetLabel;
    private JTextField messageInputField;
    private JButton sendBtn;
    private JButton logoutBtn;

    private static final String GROUP_TARGET = "[Group] Everyone (Broadcast)";
    private String selectedTarget = GROUP_TARGET;

    public ChatGUI() {
        super("Java Sockets Chat Application");
        client.setListener(this);

        initStyles();
        buildUI();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        // Enlarged comfortable default window dimensions
        setSize(1000, 680);
        setMinimumSize(new Dimension(750, 520));
        setLocationRelativeTo(null);
    }

    private void initStyles() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
    }

    private void buildUI() {
        mainContainer.add(createLoginPanel(), VIEW_LOGIN);
        mainContainer.add(createChatPanel(), VIEW_CHAT);
        add(mainContainer);
        cardLayout.show(mainContainer, VIEW_LOGIN);
    }

    // ==========================================
    // 1. LOGIN & REGISTRATION PANEL
    // ==========================================
    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(245, 247, 250));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Card / Container Box
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(218, 224, 233), 1, true),
            BorderFactory.createEmptyBorder(30, 35, 30, 35)
        ));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel title = new JLabel("Chat Application Login", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(new Color(33, 43, 54));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        card.add(title, c);

        JLabel subtitle = new JLabel("JDK 25 • Sockets • MySQL • Swing", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(110, 120, 135));
        c.gridy = 1;
        card.add(subtitle, c);

        c.gridwidth = 1;

        // Server Host & Port
        JLabel hostLbl = new JLabel("Server Host:");
        hostLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        c.gridy = 2; c.gridx = 0;
        card.add(hostLbl, c);
        hostField = new JTextField("localhost", 15);
        hostField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        hostField.setPreferredSize(new Dimension(180, 32));
        c.gridx = 1;
        card.add(hostField, c);

        JLabel portLbl = new JLabel("Port:");
        portLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        c.gridy = 3; c.gridx = 0;
        card.add(portLbl, c);
        portField = new JTextField("12345", 15);
        portField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        portField.setPreferredSize(new Dimension(180, 32));
        c.gridx = 1;
        card.add(portField, c);

        // Username
        JLabel userLbl = new JLabel("Username:");
        userLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        c.gridy = 4; c.gridx = 0;
        card.add(userLbl, c);
        usernameField = new JTextField(15);
        usernameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        usernameField.setPreferredSize(new Dimension(180, 32));
        c.gridx = 1;
        card.add(usernameField, c);

        // Password
        JLabel passLbl = new JLabel("Password:");
        passLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        c.gridy = 5; c.gridx = 0;
        card.add(passLbl, c);
        passwordField = new JPasswordField(15);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        passwordField.setPreferredSize(new Dimension(180, 32));
        c.gridx = 1;
        card.add(passwordField, c);

        // Status Label
        authStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        authStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        authStatusLabel.setForeground(new Color(217, 48, 37));
        c.gridy = 6; c.gridx = 0; c.gridwidth = 2;
        card.add(authStatusLabel, c);

        // Buttons Panel
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 5));
        btnPanel.setOpaque(false);

        loginBtn = new JButton("Log In");
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        loginBtn.setPreferredSize(new Dimension(110, 36));
        loginBtn.setBackground(new Color(26, 115, 232));
        loginBtn.setForeground(Color.BLACK);
        loginBtn.addActionListener(e -> attemptAuth(true));

        registerBtn = new JButton("Register New User");
        registerBtn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        registerBtn.setPreferredSize(new Dimension(170, 36));
        registerBtn.addActionListener(e -> attemptAuth(false));

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        c.gridy = 7;
        card.add(btnPanel, c);

        // Pressing enter triggers login
        passwordField.addActionListener(e -> attemptAuth(true));
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());

        panel.add(card, gbc);
        return panel;
    }

    private void attemptAuth(boolean isLogin) {
        String host = hostField.getText().trim();
        String portStr = portField.getText().trim();
        String user = usernameField.getText().trim();
        String pass = new String(passwordField.getPassword()).trim();

        if (user.isEmpty() || pass.isEmpty()) {
            authStatusLabel.setText("Please enter both username and password.");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            authStatusLabel.setText("Invalid port number.");
            return;
        }

        authStatusLabel.setForeground(new Color(30, 100, 200));
        authStatusLabel.setText(isLogin ? "Connecting and logging in..." : "Connecting and registering...");
        loginBtn.setEnabled(false);
        registerBtn.setEnabled(false);

        // Run socket connection off the EDT
        new Thread(() -> {
            try {
                if (!client.isConnected()) {
                    client.connect(host, port);
                }
                if (isLogin) {
                    client.sendLogin(user, pass);
                } else {
                    client.sendRegister(user, pass);
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    authStatusLabel.setForeground(new Color(217, 48, 37));
                    authStatusLabel.setText("Could not connect to server at " + host + ":" + port);
                    loginBtn.setEnabled(true);
                    registerBtn.setEnabled(true);
                });
            }
        }).start();
    }

    // ==========================================
    // 2. MAIN CHAT PANEL (ENLARGED TYPOGRAPHY)
    // ==========================================
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // --- Top Bar ---
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(8, 12, 12, 12)
        ));

        currentUserLabel = new JLabel("Logged in as: -");
        currentUserLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));

        logoutBtn = new JButton("Disconnect & Logout");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        logoutBtn.setPreferredSize(new Dimension(170, 34));
        logoutBtn.addActionListener(e -> {
            client.disconnect();
            cardLayout.show(mainContainer, VIEW_LOGIN);
            setTitle("Java Sockets Chat Application");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            authStatusLabel.setForeground(Color.GRAY);
            authStatusLabel.setText("Logged out successfully.");
        });

        topBar.add(currentUserLabel, BorderLayout.WEST);
        topBar.add(logoutBtn, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // --- Center: Chat History + Online User List ---
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        doc = chatPane.getStyledDocument();
        initTextStyles();

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 205, 215), 1),
            "Chat Log",
            0, 0,
            new Font("Segoe UI", Font.BOLD, 14),
            new Color(50, 60, 75)
        ));

        // User list setup
        userListModel = new DefaultListModel<>();
        userJList = new JList<>(userListModel);
        userJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userJList.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        userJList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return c;
            }
        });

        userJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String val = userJList.getSelectedValue();
                if (val == null || val.equals(GROUP_TARGET)) {
                    selectedTarget = GROUP_TARGET;
                    targetLabel.setText("Sending to: [Group] Everyone (Broadcast)");
                    targetLabel.setForeground(new Color(25, 118, 210));
                } else {
                    String cleaned = val.replace("● ", "").replace(" (You)", "").trim();
                    selectedTarget = cleaned;
                    targetLabel.setText("Sending to: [Private] " + cleaned);
                    targetLabel.setForeground(new Color(156, 39, 176));
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userJList);
        userScroll.setPreferredSize(new Dimension(230, 300));
        userScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(200, 205, 215), 1),
            "Active Contacts",
            0, 0,
            new Font("Segoe UI", Font.BOLD, 14),
            new Color(50, 60, 75)
        ));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userScroll);
        splitPane.setResizeWeight(0.75);
        panel.add(splitPane, BorderLayout.CENTER);

        // --- Bottom: Input & Send Bar ---
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(8, 6, 6, 6));

        targetLabel = new JLabel("Sending to: [Group] Everyone (Broadcast)");
        targetLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        targetLabel.setForeground(new Color(25, 118, 210));
        bottomPanel.add(targetLabel, BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        messageInputField = new JTextField();
        messageInputField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        messageInputField.setPreferredSize(new Dimension(messageInputField.getPreferredSize().width, 40));
        messageInputField.addActionListener(e -> sendMessage());

        sendBtn = new JButton("Send");
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        sendBtn.setPreferredSize(new Dimension(95, 40));
        sendBtn.addActionListener(e -> sendMessage());

        inputRow.add(messageInputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        bottomPanel.add(inputRow, BorderLayout.CENTER);

        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void initTextStyles() {
        Style defaultStyle = doc.addStyle("default", null);
        StyleConstants.setFontFamily(defaultStyle, "Segoe UI");
        StyleConstants.setFontSize(defaultStyle, 16);
        StyleConstants.setForeground(defaultStyle, Color.BLACK);

        Style timeStyle = doc.addStyle("time", defaultStyle);
        StyleConstants.setFontSize(timeStyle, 14);
        StyleConstants.setForeground(timeStyle, new Color(120, 130, 140));

        Style groupSender = doc.addStyle("groupSender", defaultStyle);
        StyleConstants.setFontSize(groupSender, 16);
        StyleConstants.setBold(groupSender, true);
        StyleConstants.setForeground(groupSender, new Color(26, 115, 232));

        Style privateSender = doc.addStyle("privateSender", defaultStyle);
        StyleConstants.setFontSize(privateSender, 16);
        StyleConstants.setBold(privateSender, true);
        StyleConstants.setForeground(privateSender, new Color(156, 39, 176));

        Style privateSentSender = doc.addStyle("privateSentSender", defaultStyle);
        StyleConstants.setFontSize(privateSentSender, 16);
        StyleConstants.setBold(privateSentSender, true);
        StyleConstants.setForeground(privateSentSender, new Color(0, 137, 123));

        Style systemStyle = doc.addStyle("system", defaultStyle);
        StyleConstants.setFontSize(systemStyle, 15);
        StyleConstants.setItalic(systemStyle, true);
        StyleConstants.setForeground(systemStyle, new Color(100, 100, 100));

        Style historyStyle = doc.addStyle("history", defaultStyle);
        StyleConstants.setFontSize(historyStyle, 15);
        StyleConstants.setItalic(historyStyle, true);
        StyleConstants.setForeground(historyStyle, new Color(130, 119, 23));
    }

    private void appendStyledMessage(String styleName, String text) {
        try {
            doc.insertString(doc.getLength(), text, doc.getStyle(styleName));
            chatPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String msg = messageInputField.getText().trim();
        if (msg.isEmpty()) return;

        if (selectedTarget == null || selectedTarget.equals(GROUP_TARGET)) {
            client.sendBroadcast(msg);
        } else {
            client.sendPrivate(selectedTarget, msg);
        }
        messageInputField.setText("");
        messageInputField.requestFocusInWindow();
    }

    private void handleExit() {
        int confirm = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to disconnect and exit?",
            "Exit Chat Application",
            JOptionPane.YES_NO_OPTION
        );
        if (confirm == JOptionPane.YES_OPTION) {
            client.disconnect();
            dispose();
            System.exit(0);
        }
    }

    // ==========================================
    // 3. CHAT EVENT LISTENER IMPLEMENTATION
    // (All dispatched on Swing EDT)
    // ==========================================

    @Override
    public void onLoginSuccess(String username) {
        SwingUtilities.invokeLater(() -> {
            currentUserLabel.setText("Logged in as: " + username);
            setTitle("Java Sockets Chat Application - " + username);
            authStatusLabel.setText("");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);

            // Clear previous chat pane
            chatPane.setText("");
            appendStyledMessage("system", "=== Connected to Chat Server ===\n");

            cardLayout.show(mainContainer, VIEW_CHAT);
            messageInputField.requestFocusInWindow();
        });
    }

    @Override
    public void onLoginFailure(String reason) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setForeground(new Color(217, 48, 37));
            authStatusLabel.setText(reason);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
        });
    }

    @Override
    public void onRegisterSuccess(String message) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setForeground(new Color(46, 125, 50));
            authStatusLabel.setText(message);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            JOptionPane.showMessageDialog(this, message, "Registration Success", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onRegisterFailure(String reason) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setForeground(new Color(217, 48, 37));
            authStatusLabel.setText(reason);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
        });
    }

    @Override
    public void onBroadcastReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("groupSender", "[GROUP] " + sender + ": ");
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onPrivateReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("privateSender", "[PRIVATE from " + sender + "]: ");
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onPrivateSent(String timestamp, String recipient, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("privateSentSender", "[PRIVATE to " + recipient + "]: ");
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onSystemMessage(String timestamp, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("system", "*** " + message + " ***\n");
        });
    }

    @Override
    public void onUserListUpdated(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            String currentSelection = userJList.getSelectedValue();
            userListModel.clear();
            userListModel.addElement(GROUP_TARGET);

            String me = client.getCurrentUsername();
            for (String u : users) {
                if (me != null && u.equalsIgnoreCase(me)) {
                    userListModel.addElement("● " + u + " (You)");
                } else {
                    userListModel.addElement("● " + u);
                }
            }

            // Restore selection if still present
            if (currentSelection != null && userListModel.contains(currentSelection)) {
                userJList.setSelectedValue(currentSelection, true);
            } else {
                userJList.setSelectedIndex(0);
            }
        });
    }

    @Override
    public void onHistoryMessage(String timestamp, String sender, String receiver, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            if ("ALL".equalsIgnoreCase(receiver)) {
                appendStyledMessage("groupSender", "[HIST/GROUP] " + sender + ": ");
            } else {
                appendStyledMessage("privateSender", "[HIST/PRIV " + sender + " -> " + receiver + "]: ");
            }
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onHistoryDone() {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("history", "---------------- History Loaded ----------------\n");
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainContainer, VIEW_LOGIN);
            authStatusLabel.setForeground(new Color(217, 48, 37));
            authStatusLabel.setText(reason);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ChatGUI gui = new ChatGUI();
            gui.setVisible(true);
        });
    }
}
