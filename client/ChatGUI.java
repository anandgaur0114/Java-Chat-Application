package client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
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
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * ChatGUI provides a modern, high-DPI graphical interface for the chat application.
 * Demonstrates:
 *  - Modern UI design in Java Swing (CardLayout, anti-aliased custom painting)
 *  - Event Dispatch Thread (EDT) safety via SwingUtilities.invokeLater
 *  - StyledDocument in JTextPane for rich, multi-colored chat streams
 *  - Custom CellRenderer for real-time contact presence with status badges
 *  - Dynamic target indicator for Broadcast vs Private messaging
 */
public class ChatGUI extends JFrame implements ChatClient.ChatEventListener {

    // Theme Color Palette (Modern Slate / Indigo)
    private static final Color COLOR_PRIMARY = new Color(79, 70, 229);       // Indigo 600
    private static final Color COLOR_PRIMARY_HOVER = new Color(67, 56, 202); // Indigo 700
    private static final Color COLOR_NAVY_BAR = new Color(15, 23, 42);       // Slate 900
    private static final Color COLOR_BG_CANVAS = new Color(241, 245, 249);   // Slate 100
    private static final Color COLOR_BORDER = new Color(226, 232, 240);      // Slate 200
    private static final Color COLOR_TEXT_MAIN = new Color(30, 41, 59);      // Slate 800
    private static final Color COLOR_ONLINE_GREEN = new Color(16, 185, 129); // Emerald 500
    private static final Color COLOR_LOGOUT_RED = new Color(225, 29, 72);    // Rose 600

    private static final String VIEW_LOGIN = "LOGIN_VIEW";
    private static final String VIEW_CHAT = "CHAT_VIEW";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel mainContainer = new JPanel(cardLayout);
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
    private JLabel userStatusLabel;
    private JTextPane chatPane;
    private StyledDocument doc;
    private DefaultListModel<String> userListModel;
    private JList<String> userJList;
    private JLabel targetIndicator;
    private JPanel targetChipPanel;
    private JTextField messageInputField;
    private JButton sendBtn;
    private JButton logoutBtn;
    private JLabel contactsCountLabel;

    private static final String GROUP_TARGET = "Everyone (Group Broadcast)";
    private String selectedTarget = GROUP_TARGET;

    public ChatGUI() {
        super("Java Sockets Chat Application");
        client.setListener(this);

        initSystemRendering();
        buildUI();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleExit();
            }
        });

        setSize(1020, 700);
        setMinimumSize(new Dimension(800, 560));
        setLocationRelativeTo(null);
    }

    /**
     * Enables system antialiasing for crisp text rendering on high-DPI monitors.
     */
    private void initSystemRendering() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
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

    // =========================================================================
    // 1. MODERN LOGIN & REGISTRATION PANEL
    // =========================================================================
    private JPanel createLoginPanel() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(COLOR_BG_CANVAS);

        // Floating Card with rounded shadow effect
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(COLOR_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(35, 45, 35, 45));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Header Title
        JLabel title = new JLabel("Java Chat Application", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(COLOR_TEXT_MAIN);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        card.add(title, c);

        JLabel subtitle = new JLabel("JDK 25 • TCP Sockets • MySQL 8.0 • Swing", SwingConstants.CENTER);
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(100, 116, 139));
        c.gridy = 1;
        card.add(subtitle, c);

        c.gridwidth = 1;

        // Server Host & Port Row
        c.gridy = 2; c.gridx = 0;
        card.add(createFieldLabel("Server Host:"), c);
        hostField = createStyledTextField("localhost");
        c.gridx = 1;
        card.add(hostField, c);

        c.gridy = 3; c.gridx = 0;
        card.add(createFieldLabel("TCP Port:"), c);
        portField = createStyledTextField("12345");
        c.gridx = 1;
        card.add(portField, c);

        // Username & Password Row
        c.gridy = 4; c.gridx = 0;
        card.add(createFieldLabel("Username:"), c);
        usernameField = createStyledTextField("");
        c.gridx = 1;
        card.add(usernameField, c);

        c.gridy = 5; c.gridx = 0;
        card.add(createFieldLabel("Password:"), c);
        passwordField = new JPasswordField();
        styleInputField(passwordField);
        c.gridx = 1;
        card.add(passwordField, c);

        // Status Label
        authStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        authStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        authStatusLabel.setForeground(COLOR_LOGOUT_RED);
        c.gridy = 6; c.gridx = 0; c.gridwidth = 2;
        card.add(authStatusLabel, c);

        // Action Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 5));
        btnPanel.setOpaque(false);

        loginBtn = createModernButton("Log In", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 120, 42);
        loginBtn.addActionListener(e -> attemptAuth(true));

        registerBtn = createModernButton("Register New User", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 180, 42);
        registerBtn.addActionListener(e -> attemptAuth(false));

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        c.gridy = 7;
        card.add(btnPanel, c);

        passwordField.addActionListener(e -> attemptAuth(true));
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());

        background.add(card);
        return background;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lbl.setForeground(COLOR_TEXT_MAIN);
        return lbl;
    }

    private JTextField createStyledTextField(String text) {
        JTextField tf = new JTextField(text, 14);
        styleInputField(tf);
        return tf;
    }

    private void styleInputField(JTextField tf) {
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        tf.setPreferredSize(new Dimension(200, 36));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_BORDER, 1, true),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));
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

        authStatusLabel.setForeground(COLOR_PRIMARY);
        authStatusLabel.setText(isLogin ? "Connecting & verifying..." : "Connecting & registering...");
        loginBtn.setEnabled(false);
        registerBtn.setEnabled(false);

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
                    authStatusLabel.setForeground(COLOR_LOGOUT_RED);
                    authStatusLabel.setText("Could not connect to server at " + host + ":" + port);
                    loginBtn.setEnabled(true);
                    registerBtn.setEnabled(true);
                });
            }
        }).start();
    }

    // =========================================================================
    // 2. MODERN CHAT MAIN PANEL (DISCORD / SLACK INSPIRED)
    // =========================================================================
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(COLOR_BG_CANVAS);

        // --- Top Bar: Premium Dark Slate Header ---
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setBackground(COLOR_NAVY_BAR);
        topBar.setBorder(new EmptyBorder(12, 20, 12, 20));

        // Left: Avatar + Username + Online status
        JPanel userMetaPanel = new JPanel();
        userMetaPanel.setLayout(new BoxLayout(userMetaPanel, BoxLayout.Y_AXIS));
        userMetaPanel.setOpaque(false);

        currentUserLabel = new JLabel("Logged in as: -");
        currentUserLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        currentUserLabel.setForeground(Color.WHITE);

        userStatusLabel = new JLabel("● Online  •  Connected to Port 12345");
        userStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userStatusLabel.setForeground(new Color(52, 211, 153)); // Soft emerald

        userMetaPanel.add(currentUserLabel);
        userMetaPanel.add(Box.createVerticalStrut(2));
        userMetaPanel.add(userStatusLabel);
        topBar.add(userMetaPanel, BorderLayout.WEST);

        // Right: Disconnect Button
        logoutBtn = createModernButton("Disconnect & Logout", new Color(30, 41, 59), COLOR_LOGOUT_RED, Color.WHITE, 180, 36);
        logoutBtn.addActionListener(e -> {
            client.disconnect();
            cardLayout.show(mainContainer, VIEW_LOGIN);
            setTitle("Java Sockets Chat Application");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            authStatusLabel.setForeground(Color.GRAY);
            authStatusLabel.setText("Logged out successfully.");
        });
        topBar.add(logoutBtn, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // --- Center Area: Chat Log + Contact Roster ---
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBorder(new EmptyBorder(12, 14, 8, 14));
        centerContainer.setOpaque(false);

        // Chat Pane
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(Color.WHITE);
        chatPane.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        chatPane.setBorder(new EmptyBorder(10, 14, 10, 14));
        doc = chatPane.getStyledDocument();
        initTextStyles();

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(COLOR_BORDER, 1, true),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));

        // Right: Online Users Sidebar
        JPanel userListContainer = new JPanel(new BorderLayout(0, 8));
        userListContainer.setOpaque(false);

        JPanel userListHeader = new JPanel(new BorderLayout());
        userListHeader.setOpaque(false);
        JLabel contactsTitle = new JLabel("ACTIVE CONTACTS");
        contactsTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        contactsTitle.setForeground(new Color(100, 116, 139));

        contactsCountLabel = new JLabel("0 online");
        contactsCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        contactsCountLabel.setForeground(COLOR_ONLINE_GREEN);

        userListHeader.add(contactsTitle, BorderLayout.WEST);
        userListHeader.add(contactsCountLabel, BorderLayout.EAST);
        userListContainer.add(userListHeader, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userJList = new JList<>(userListModel);
        userJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userJList.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        userJList.setBackground(Color.WHITE);
        userJList.setCellRenderer(new ModernContactRenderer());

        userJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String val = userJList.getSelectedValue();
                if (val == null || val.contains(GROUP_TARGET)) {
                    selectedTarget = GROUP_TARGET;
                    updateTargetChip(true, "Everyone (Broadcast)");
                } else {
                    String cleaned = val.replace("●", "").replace("(You)", "").trim();
                    selectedTarget = cleaned;
                    updateTargetChip(false, cleaned);
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userJList);
        userScroll.setPreferredSize(new Dimension(240, 300));
        userScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1, true));
        userListContainer.add(userScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, chatScroll, userListContainer);
        splitPane.setResizeWeight(0.76);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        centerContainer.add(splitPane, BorderLayout.CENTER);
        panel.add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Area: Target Pill + Input + Send Button ---
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 8));
        bottomContainer.setBorder(new EmptyBorder(6, 14, 14, 14));
        bottomContainer.setOpaque(false);

        // Modern Target Chip
        targetChipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        targetChipPanel.setOpaque(false);
        targetIndicator = new JLabel("🌐 Destination: Everyone (Group Broadcast)");
        targetIndicator.setFont(new Font("Segoe UI", Font.BOLD, 13));
        targetIndicator.setForeground(COLOR_PRIMARY);
        targetChipPanel.add(targetIndicator);
        bottomContainer.add(targetChipPanel, BorderLayout.NORTH);

        // Input Row
        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);

        messageInputField = new JTextField();
        messageInputField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        messageInputField.setPreferredSize(new Dimension(messageInputField.getPreferredSize().width, 44));
        messageInputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));
        messageInputField.addActionListener(e -> sendMessage());

        sendBtn = createModernButton("Send", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 100, 44);
        sendBtn.addActionListener(e -> sendMessage());

        inputRow.add(messageInputField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        bottomContainer.add(inputRow, BorderLayout.CENTER);

        panel.add(bottomContainer, BorderLayout.SOUTH);
        return panel;
    }

    private void updateTargetChip(boolean isBroadcast, String name) {
        if (isBroadcast) {
            targetIndicator.setText("🌐 Destination: Everyone (Group Broadcast)");
            targetIndicator.setForeground(COLOR_PRIMARY);
        } else {
            targetIndicator.setText("🔒 Private Message to: " + name);
            targetIndicator.setForeground(new Color(147, 51, 234)); // Purple 600
        }
    }

    /**
     * Custom List Cell Renderer with rounded pill selection and status badges.
     */
    private static class ModernContactRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(8, 12, 8, 12));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 15));

            String text = String.valueOf(value);
            if (isSelected) {
                label.setBackground(new Color(238, 242, 255)); // Soft indigo highlight
                label.setForeground(new Color(49, 46, 129));
            } else {
                label.setBackground(Color.WHITE);
                label.setForeground(COLOR_TEXT_MAIN);
            }

            if (text.contains(GROUP_TARGET)) {
                label.setText("🌐  " + text);
                label.setFont(new Font("Segoe UI", Font.BOLD, 14));
            } else {
                label.setText("●  " + text);
            }
            return label;
        }
    }

    private void initTextStyles() {
        Style defaultStyle = doc.addStyle("default", null);
        StyleConstants.setFontFamily(defaultStyle, "Segoe UI");
        StyleConstants.setFontSize(defaultStyle, 16);
        StyleConstants.setForeground(defaultStyle, COLOR_TEXT_MAIN);

        Style timeStyle = doc.addStyle("time", defaultStyle);
        StyleConstants.setFontSize(timeStyle, 13);
        StyleConstants.setForeground(timeStyle, new Color(148, 163, 184)); // Slate 400

        Style groupSender = doc.addStyle("groupSender", defaultStyle);
        StyleConstants.setFontSize(groupSender, 16);
        StyleConstants.setBold(groupSender, true);
        StyleConstants.setForeground(groupSender, new Color(37, 99, 235)); // Blue 600

        Style privateSender = doc.addStyle("privateSender", defaultStyle);
        StyleConstants.setFontSize(privateSender, 16);
        StyleConstants.setBold(privateSender, true);
        StyleConstants.setForeground(privateSender, new Color(147, 51, 234)); // Purple 600

        Style privateSentSender = doc.addStyle("privateSentSender", defaultStyle);
        StyleConstants.setFontSize(privateSentSender, 16);
        StyleConstants.setBold(privateSentSender, true);
        StyleConstants.setForeground(privateSentSender, new Color(13, 148, 136)); // Teal 600

        Style systemStyle = doc.addStyle("system", defaultStyle);
        StyleConstants.setFontSize(systemStyle, 14);
        StyleConstants.setItalic(systemStyle, true);
        StyleConstants.setForeground(systemStyle, new Color(100, 116, 139));

        Style historyStyle = doc.addStyle("history", defaultStyle);
        StyleConstants.setFontSize(historyStyle, 14);
        StyleConstants.setBold(historyStyle, true);
        StyleConstants.setForeground(historyStyle, new Color(217, 119, 6)); // Amber 600
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

    /**
     * Helper to create anti-aliased modern rounded buttons without external dependencies.
     */
    public static JButton createModernButton(String text, Color bgColor, Color hoverColor, Color fgColor, int width, int height) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(hoverColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(hoverColor);
                } else {
                    g2.setColor(bgColor);
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setForeground(fgColor);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setPreferredSize(new Dimension(width, height));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // =========================================================================
    // 3. CHAT EVENT LISTENER CALLBACKS (Swing EDT Safe)
    // =========================================================================

    @Override
    public void onLoginSuccess(String username) {
        SwingUtilities.invokeLater(() -> {
            currentUserLabel.setText(username);
            userStatusLabel.setText("● Active Now  •  Connected to Port 12345");
            setTitle("Java Sockets Chat Room - " + username);
            authStatusLabel.setText("");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);

            chatPane.setText("");
            appendStyledMessage("system", "=== Connected to Central Server ===\n");

            cardLayout.show(mainContainer, VIEW_CHAT);
            messageInputField.requestFocusInWindow();
        });
    }

    @Override
    public void onLoginFailure(String reason) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setForeground(COLOR_LOGOUT_RED);
            authStatusLabel.setText(reason);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
        });
    }

    @Override
    public void onRegisterSuccess(String message) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setForeground(COLOR_ONLINE_GREEN);
            authStatusLabel.setText(message);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            JOptionPane.showMessageDialog(this, message, "Registration Success", JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void onRegisterFailure(String reason) {
        SwingUtilities.invokeLater(() -> {
            authStatusLabel.setForeground(COLOR_LOGOUT_RED);
            authStatusLabel.setText(reason);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
        });
    }

    @Override
    public void onBroadcastReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("groupSender", sender + ": ");
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onPrivateReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("privateSender", "[Private from " + sender + "]: ");
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onPrivateSent(String timestamp, String recipient, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("privateSentSender", "[Private to " + recipient + "]: ");
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onSystemMessage(String timestamp, String message) {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("time", "[" + timestamp + "] ");
            appendStyledMessage("system", "— " + message + " —\n");
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
                    userListModel.addElement(u + " (You)");
                } else {
                    userListModel.addElement(u);
                }
            }

            if (contactsCountLabel != null) {
                contactsCountLabel.setText(users.size() + " online");
            }

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
                appendStyledMessage("groupSender", "[History] " + sender + ": ");
            } else {
                appendStyledMessage("privateSender", "[History " + sender + " → " + receiver + "]: ");
            }
            appendStyledMessage("default", message + "\n");
        });
    }

    @Override
    public void onHistoryDone() {
        SwingUtilities.invokeLater(() -> {
            appendStyledMessage("history", "──────── Historical Messages Restored ────────\n");
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainContainer, VIEW_LOGIN);
            authStatusLabel.setForeground(COLOR_LOGOUT_RED);
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
