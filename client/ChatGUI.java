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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * ChatGUI provides a presentation-grade, modern desktop interface rendering 
 * rich HTML/CSS chat bubbles (WhatsApp / Slack / Discord aesthetic) for each conversation.
 * Demonstrates:
 *  - HTML/CSS rendering in Java Swing (JEditorPane with speech bubbles)
 *  - Per-user conversation decoupling (JTabbedPane & Detachable Pop-Out Windows)
 *  - Event Dispatch Thread (EDT) safety via SwingUtilities.invokeLater
 *  - Live technical telemetry bar (TCP Socket status, MySQL persistence, SHA-256)
 *  - Interactive Presentation & Architecture dialog for lab examiners
 */
public class ChatGUI extends JFrame implements ChatClient.ChatEventListener {

    // Modern Palette (Slate & Indigo)
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

    // Login Components
    private JTextField hostField;
    private JTextField portField;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel authStatusLabel;
    private JButton loginBtn;
    private JButton registerBtn;

    // Chat View Components
    private JLabel currentUserLabel;
    private JLabel userStatusLabel;
    private JTabbedPane tabbedPane;
    private DefaultListModel<String> userListModel;
    private JList<String> userJList;
    private JLabel targetIndicator;
    private JTextField messageInputField;
    private JButton sendBtn;
    private JButton logoutBtn;
    private JLabel contactsCountLabel;

    private static final String GROUP_TARGET = "Everyone (Group Broadcast)";
    private String selectedTarget = null; // null = Group broadcast

    // Conversation windows map: <Key, ConversationWindow>
    private final Map<String, ConversationWindow> conversations = new LinkedHashMap<>();

    // HTML Base Template for Message Bubbles
    private static final String HTML_HEADER = 
        "<html><head><style>" +
        "body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; margin: 8px 12px; background-color: #F8FAFC; }" +
        "</style></head><body>";
    private static final String HTML_FOOTER = "</body></html>";

    public ChatGUI() {
        super("Java Sockets Chat Application - Presentation Edition");
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

        setSize(1060, 740);
        setMinimumSize(new Dimension(840, 600));
        setLocationRelativeTo(null);
    }

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
    // 1. MODERN PRESENTATION LOGIN PANEL
    // =========================================================================
    private JPanel createLoginPanel() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(COLOR_BG_CANVAS);

        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(COLOR_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(35, 45, 30, 45));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Rich HTML Title Banner
        JLabel title = new JLabel(
            "<html><center>" +
            "<font face='Segoe UI' size='6' color='#1E293B'><b>💬 Java Sockets Chat Room</b></font><br>" +
            "<font face='Segoe UI' size='3' color='#4F46E5'><b>Advanced Java Lab Presentation</b></font><br>" +
            "<font face='Segoe UI' size='2' color='#64748B'>JDK 25 • TCP Sockets • MySQL 8.0 • Swing HTML/CSS</font>" +
            "</center></html>", SwingConstants.CENTER
        );
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        card.add(title, c);

        c.gridwidth = 1;

        c.gridy = 1; c.gridx = 0;
        card.add(createFieldLabel("Server Host:"), c);
        hostField = createStyledTextField("localhost");
        c.gridx = 1;
        card.add(hostField, c);

        c.gridy = 2; c.gridx = 0;
        card.add(createFieldLabel("TCP Port:"), c);
        portField = createStyledTextField("12345");
        c.gridx = 1;
        card.add(portField, c);

        c.gridy = 3; c.gridx = 0;
        card.add(createFieldLabel("Username:"), c);
        usernameField = createStyledTextField("");
        c.gridx = 1;
        card.add(usernameField, c);

        c.gridy = 4; c.gridx = 0;
        card.add(createFieldLabel("Password:"), c);
        passwordField = new JPasswordField();
        styleInputField(passwordField);
        c.gridx = 1;
        card.add(passwordField, c);

        authStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        authStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        authStatusLabel.setForeground(COLOR_LOGOUT_RED);
        c.gridy = 5; c.gridx = 0; c.gridwidth = 2;
        card.add(authStatusLabel, c);

        // Buttons Row
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 5));
        btnPanel.setOpaque(false);

        loginBtn = createModernButton("Log In", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 120, 42);
        loginBtn.addActionListener(e -> attemptAuth(true));

        registerBtn = createModernButton("Register New User", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 180, 42);
        registerBtn.addActionListener(e -> attemptAuth(false));

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        c.gridy = 6;
        card.add(btnPanel, c);

        // Demo Tip Label
        JLabel tipLabel = new JLabel(
            "<html><center><font face='Segoe UI' size='2' color='#64748B'>" +
            "💡 <b>Demo Tip:</b> Pre-seeded users: <b>Anand</b> / <b>password123</b> or <b>Sunanda</b> / <b>password123</b>" +
            "</font></center></html>", SwingConstants.CENTER
        );
        c.gridy = 7;
        card.add(tipLabel, c);

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
        tf.setPreferredSize(new Dimension(200, 38));
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
    // 2. MAIN CHAT PANEL (RICH HTML CHAT BUBBLES + STATUS TELEMETRY)
    // =========================================================================
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(COLOR_BG_CANVAS);

        // --- Top Bar: Premium Dark Slate with Live Badges ---
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setBackground(COLOR_NAVY_BAR);
        topBar.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel userMetaPanel = new JPanel();
        userMetaPanel.setLayout(new BoxLayout(userMetaPanel, BoxLayout.Y_AXIS));
        userMetaPanel.setOpaque(false);

        currentUserLabel = new JLabel("Logged in as: -");
        currentUserLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        currentUserLabel.setForeground(Color.WHITE);

        userStatusLabel = new JLabel("● Online  •  Port 12345");
        userStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        userStatusLabel.setForeground(new Color(52, 211, 153));

        userMetaPanel.add(currentUserLabel);
        userMetaPanel.add(Box.createVerticalStrut(2));
        userMetaPanel.add(userStatusLabel);
        topBar.add(userMetaPanel, BorderLayout.WEST);

        // Right Action Controls (Architecture Info + Logout)
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topActions.setOpaque(false);

        JButton infoBtn = createModernButton("ℹ Presentation Info", new Color(30, 41, 59), new Color(51, 65, 85), Color.WHITE, 160, 36);
        infoBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        infoBtn.addActionListener(e -> showPresentationInfoDialog());
        topActions.add(infoBtn);

        logoutBtn = createModernButton("Disconnect & Logout", new Color(30, 41, 59), COLOR_LOGOUT_RED, Color.WHITE, 180, 36);
        logoutBtn.addActionListener(e -> {
            closeAllPoppedOutFrames();
            client.disconnect();
            cardLayout.show(mainContainer, VIEW_LOGIN);
            setTitle("Java Sockets Chat Application");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            authStatusLabel.setForeground(Color.GRAY);
            authStatusLabel.setText("Logged out successfully.");
        });
        topActions.add(logoutBtn);
        topBar.add(topActions, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // --- Center Area: Tabbed Multi-Window System + Contact Sidebar ---
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBorder(new EmptyBorder(10, 14, 6, 14));
        centerContainer.setOpaque(false);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 14));
        tabbedPane.setBackground(Color.WHITE);

        tabbedPane.addChangeListener(e -> {
            int selectedIdx = tabbedPane.getSelectedIndex();
            if (selectedIdx != -1) {
                Component comp = tabbedPane.getComponentAt(selectedIdx);
                for (ConversationWindow win : conversations.values()) {
                    if (win.containerPanel == comp) {
                        selectedTarget = win.targetUser;
                        updateTargetChip(win.targetUser == null, win.displayName);
                        tabbedPane.setTitleAt(selectedIdx, win.getTabTitle());
                        break;
                    }
                }
            }
        });

        // Contacts Sidebar
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
                    getOrCreateConversation(null, "Group Chat", true);
                } else {
                    String cleaned = val.replace("●", "").replace("(You)", "").trim();
                    String me = client.getCurrentUsername();
                    if (me == null || !cleaned.equalsIgnoreCase(me)) {
                        getOrCreateConversation(cleaned, cleaned, true);
                    }
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userJList);
        userScroll.setPreferredSize(new Dimension(240, 300));
        userScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1, true));
        userListContainer.add(userScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedPane, userListContainer);
        splitPane.setResizeWeight(0.76);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        centerContainer.add(splitPane, BorderLayout.CENTER);
        panel.add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Area: Target Chip + Message Input + Technical Telemetry ---
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 6));
        bottomContainer.setBorder(new EmptyBorder(4, 14, 10, 14));
        bottomContainer.setOpaque(false);

        JPanel targetChipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
        targetChipPanel.setOpaque(false);
        targetIndicator = new JLabel("🌐 Active Window: Everyone (Group Broadcast)");
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

        // Live Technical Telemetry Bar for Lab Presentation
        JPanel telemetryBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 2));
        telemetryBar.setOpaque(false);
        JLabel telemetryLabel = new JLabel(
            "<html><font face='Segoe UI' size='2' color='#64748B'>" +
            "🟢 <b>TCP Sockets</b>: Port 12345  •  " +
            "⚡ <b>Concurrency</b>: ExecutorService  •  " +
            "🗄️ <b>Database</b>: MySQL 8.0 JDBC  •  " +
            "🔒 <b>Security</b>: SHA-256 Hashing" +
            "</font></html>"
        );
        telemetryBar.add(telemetryLabel);
        bottomContainer.add(telemetryBar, BorderLayout.SOUTH);

        panel.add(bottomContainer, BorderLayout.SOUTH);
        return panel;
    }

    private void updateTargetChip(boolean isBroadcast, String name) {
        if (isBroadcast) {
            targetIndicator.setText("🌐 Active Window: Everyone (Group Broadcast)");
            targetIndicator.setForeground(COLOR_PRIMARY);
        } else {
            targetIndicator.setText("🔒 Private End-to-End Window with: " + name);
            targetIndicator.setForeground(new Color(147, 51, 234));
        }
    }

    // =========================================================================
    // 3. CONVERSATION WINDOW WITH HTML CHAT BUBBLES
    // =========================================================================
    private class ConversationWindow {
        final String targetUser; // null for group
        final String displayName;
        final JPanel containerPanel;
        final JEditorPane chatEditor;
        final JScrollPane scrollPane;
        final StringBuilder htmlBuffer = new StringBuilder();
        JFrame popOutFrame;
        boolean isPoppedOut = false;

        ConversationWindow(String targetUser, String displayName) {
            this.targetUser = targetUser;
            this.displayName = displayName;

            containerPanel = new JPanel(new BorderLayout(0, 0));
            containerPanel.setBackground(Color.WHITE);

            // Sub-header with Channel info and Pop Out button
            JPanel subHeader = new JPanel(new BorderLayout());
            subHeader.setBackground(new Color(248, 250, 252));
            subHeader.setBorder(new EmptyBorder(6, 12, 6, 12));

            String headerLabelText = (targetUser == null) 
                ? "🌐 Group Broadcast Channel (All Users)"
                : "🔒 End-to-End Private Channel: " + displayName;
            JLabel channelLabel = new JLabel(headerLabelText);
            channelLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            channelLabel.setForeground(COLOR_TEXT_MAIN);
            subHeader.add(channelLabel, BorderLayout.WEST);

            JButton popOutBtn = createModernButton("⧉ Pop Out Window", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 150, 28);
            popOutBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            popOutBtn.addActionListener(e -> togglePopOut(this));
            subHeader.add(popOutBtn, BorderLayout.EAST);

            containerPanel.add(subHeader, BorderLayout.NORTH);

            // JEditorPane configured for HTML/CSS chat bubble rendering
            chatEditor = new JEditorPane();
            chatEditor.setContentType("text/html");
            chatEditor.setEditable(false);
            chatEditor.setBackground(new Color(248, 250, 252));
            chatEditor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            chatEditor.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            chatEditor.setText(HTML_HEADER + HTML_FOOTER);

            scrollPane = new JScrollPane(chatEditor);
            scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1));
            containerPanel.add(scrollPane, BorderLayout.CENTER);
        }

        String getTabTitle() {
            return (targetUser == null) ? "🌐 Group Chat" : "🔒 " + displayName;
        }

        void appendHtmlRow(String rowHtml) {
            htmlBuffer.append(rowHtml);
            chatEditor.setText(HTML_HEADER + htmlBuffer.toString() + HTML_FOOTER);
            SwingUtilities.invokeLater(() -> {
                chatEditor.setCaretPosition(chatEditor.getDocument().getLength());
            });
        }
    }

    private synchronized ConversationWindow getOrCreateConversation(String targetUser, String displayName, boolean select) {
        String key = (targetUser == null) ? "GLOBAL" : targetUser.toLowerCase();
        ConversationWindow win = conversations.get(key);

        if (win == null) {
            win = new ConversationWindow(targetUser, displayName);
            conversations.put(key, win);
            tabbedPane.addTab(win.getTabTitle(), win.containerPanel);
        }

        if (select && !win.isPoppedOut) {
            tabbedPane.setSelectedComponent(win.containerPanel);
            selectedTarget = win.targetUser;
            updateTargetChip(win.targetUser == null, win.displayName);
        } else if (win.isPoppedOut && win.popOutFrame != null) {
            win.popOutFrame.toFront();
            win.popOutFrame.requestFocus();
        }

        return win;
    }

    private void togglePopOut(ConversationWindow win) {
        if (!win.isPoppedOut) {
            tabbedPane.remove(win.containerPanel);
            win.isPoppedOut = true;

            JFrame frame = new JFrame("Private Chat: " + win.displayName);
            frame.setSize(620, 540);
            frame.setLocationRelativeTo(this);
            frame.setLayout(new BorderLayout());

            JPanel popBottom = new JPanel(new BorderLayout(8, 0));
            popBottom.setBorder(new EmptyBorder(8, 10, 10, 10));
            JTextField popInput = new JTextField();
            popInput.setFont(new Font("Segoe UI", Font.PLAIN, 15));
            popInput.setPreferredSize(new Dimension(200, 38));

            JButton popSend = createModernButton("Send", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 80, 38);
            Runnable sendAction = () -> {
                String text = popInput.getText().trim();
                if (!text.isEmpty()) {
                    if (win.targetUser == null) {
                        client.sendBroadcast(text);
                    } else {
                        client.sendPrivate(win.targetUser, text);
                    }
                    popInput.setText("");
                }
            };
            popInput.addActionListener(e -> sendAction.run());
            popSend.addActionListener(e -> sendAction.run());

            popBottom.add(popInput, BorderLayout.CENTER);
            popBottom.add(popSend, BorderLayout.EAST);

            frame.add(win.containerPanel, BorderLayout.CENTER);
            frame.add(popBottom, BorderLayout.SOUTH);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    frame.dispose();
                    win.isPoppedOut = false;
                    tabbedPane.addTab(win.getTabTitle(), win.containerPanel);
                    tabbedPane.setSelectedComponent(win.containerPanel);
                }
            });

            win.popOutFrame = frame;
            frame.setVisible(true);
        } else {
            if (win.popOutFrame != null) {
                win.popOutFrame.dispose();
            }
            win.isPoppedOut = false;
            tabbedPane.addTab(win.getTabTitle(), win.containerPanel);
            tabbedPane.setSelectedComponent(win.containerPanel);
        }
    }

    private void closeAllPoppedOutFrames() {
        for (ConversationWindow win : conversations.values()) {
            if (win.isPoppedOut && win.popOutFrame != null) {
                win.popOutFrame.dispose();
            }
        }
    }

    // =========================================================================
    // 4. HTML CHAT BUBBLE BUILDERS (WHATSAPP / SLACK STYLING)
    // =========================================================================

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("\n", "<br>");
    }

    /**
     * Renders an incoming group broadcast bubble (Left-aligned, crisp white card).
     */
    private String buildIncomingGroupBubble(String timestamp, String sender, String message) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='left'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#FFFFFF; border:1px solid #E2E8F0;'>" +
               "<tr><td>" +
               "<font face='Segoe UI' size='3' color='#2563EB'><b>" + escapeHtml(sender) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<font face='Segoe UI' size='4' color='#1E293B'>" + escapeHtml(message) + "</font>" +
               "</td></tr></table></td></tr></table>";
    }

    /**
     * Renders an outgoing broadcast bubble sent by You (Right-aligned, soft indigo bubble).
     */
    private String buildOutgoingGroupBubble(String timestamp, String message) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='right'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#EEF2FF; border:1px solid #C7D2FE;'>" +
               "<tr><td align='right'>" +
               "<font face='Segoe UI' size='2' color='#4F46E5'><b>You</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<font face='Segoe UI' size='4' color='#1E1B4B'>" + escapeHtml(message) + "</font>" +
               "</td></tr></table></td></tr></table>";
    }

    /**
     * Renders an incoming private 1-to-1 message (Left-aligned, luxury purple card).
     */
    private String buildIncomingPrivateBubble(String timestamp, String sender, String message) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='left'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#FAF5FF; border:1px solid #E9D5FF;'>" +
               "<tr><td>" +
               "<font face='Segoe UI' size='3' color='#7C3AED'><b>🔒 " + escapeHtml(sender) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<font face='Segoe UI' size='4' color='#1E293B'>" + escapeHtml(message) + "</font>" +
               "</td></tr></table></td></tr></table>";
    }

    /**
     * Renders an outgoing private 1-to-1 message (Right-aligned, soft teal bubble).
     */
    private String buildOutgoingPrivateBubble(String timestamp, String message) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='right'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#F0FDF4; border:1px solid #BBF7D0;'>" +
               "<tr><td align='right'>" +
               "<font face='Segoe UI' size='2' color='#0D9488'><b>🔒 You</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<font face='Segoe UI' size='4' color='#064E3B'>" + escapeHtml(message) + "</font>" +
               "</td></tr></table></td></tr></table>";
    }

    /**
     * Renders a centered system notice chip.
     */
    private String buildSystemNotice(String text) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin:6px 0;'>" +
               "<tr><td align='center'>" +
               "<font face='Segoe UI' size='2' color='#64748B'><i>— " + escapeHtml(text) + " —</i></font>" +
               "</td></tr></table>";
    }

    /**
     * Renders a historical message bubble loaded from MySQL.
     */
    private String buildHistoryBubble(String timestamp, String sender, String message, boolean isMe) {
        String bgColor = isMe ? "#FEF3C7" : "#FFFBEB";
        String borderColor = isMe ? "#FDE68A" : "#FEF08A";
        String align = isMe ? "right" : "left";
        String tag = isMe ? "You (History)" : sender + " (History)";

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='" + align + "'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:" + bgColor + "; border:1px solid " + borderColor + ";'>" +
               "<tr><td align='" + align + "'>" +
               "<font face='Segoe UI' size='2' color='#B45309'><b>📜 " + escapeHtml(tag) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<font face='Segoe UI' size='4' color='#1E293B'>" + escapeHtml(message) + "</font>" +
               "</td></tr></table></td></tr></table>";
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
            closeAllPoppedOutFrames();
            client.disconnect();
            dispose();
            System.exit(0);
        }
    }

    /**
     * Modal dialog presenting technical architecture to lab examiners.
     */
    private void showPresentationInfoDialog() {
        String infoHtml = 
            "<html><body style='width: 420px; font-family: Segoe UI, sans-serif; padding: 10px;'>" +
            "<h2 style='color:#4F46E5; margin-bottom: 4px;'>Java Chat Application Architecture</h2>" +
            "<p style='color:#64748B; margin-top: 0;'><b>Advanced Java Lab Presentation</b></p><hr>" +
            "<p><b>1. Networking Tier:</b> Core <code>java.net.Socket</code> & <code>ServerSocket</code> over TCP (Port 12345).</p>" +
            "<p><b>2. Concurrency Model:</b> Managed <code>ExecutorService</code> Thread Pool & <code>ConcurrentHashMap</code> session registry.</p>" +
            "<p><b>3. User Interface:</b> Java Swing with <code>HTMLEditorKit</code> chat bubbles, <code>JTabbedPane</code>, & detached windows.</p>" +
            "<p><b>4. Persistence Tier:</b> MySQL 8.0 via JDBC <code>PreparedStatement</code> (SQL-Injection protected).</p>" +
            "<p><b>5. Security:</b> One-way <code>SHA-256</code> cryptographic password digest storage.</p>" +
            "<hr>" +
            "<p><b>Team Members:</b> Abhinav Batra, Amit Kumar, Anand Gaur, Abhishek Sihag<br>" +
            "<b>ITS Coordinator / Guide:</b> Ms. Mona Nagar (Assistant Professor)</p>" +
            "</body></html>";

        JOptionPane.showMessageDialog(this, new JLabel(infoHtml), "Project Presentation Overview", JOptionPane.INFORMATION_MESSAGE);
    }

    private static class ModernContactRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setBorder(new EmptyBorder(8, 12, 8, 12));
            label.setFont(new Font("Segoe UI", Font.PLAIN, 15));

            String text = String.valueOf(value);
            if (isSelected) {
                label.setBackground(new Color(238, 242, 255));
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
    // 5. CHAT EVENT LISTENER CALLBACKS (SWING EDT SAFE)
    // =========================================================================

    @Override
    public void onLoginSuccess(String username) {
        SwingUtilities.invokeLater(() -> {
            currentUserLabel.setText(username);
            userStatusLabel.setText("● Active Now  •  Port 12345");
            setTitle("Java Sockets Chat Room - " + username);
            authStatusLabel.setText("");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);

            closeAllPoppedOutFrames();
            conversations.clear();
            tabbedPane.removeAll();

            ConversationWindow global = getOrCreateConversation(null, "Group Chat", true);
            global.appendHtmlRow(buildSystemNotice("Connected to Central Chat Server on TCP Port 12345"));

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
            ConversationWindow global = getOrCreateConversation(null, "Group Chat", false);
            String me = client.getCurrentUsername();
            if (me != null && sender.equalsIgnoreCase(me)) {
                global.appendHtmlRow(buildOutgoingGroupBubble(timestamp, message));
            } else {
                global.appendHtmlRow(buildIncomingGroupBubble(timestamp, sender, message));
            }
        });
    }

    @Override
    public void onPrivateReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(sender, sender, false);
            win.appendHtmlRow(buildIncomingPrivateBubble(timestamp, sender, message));

            int idx = tabbedPane.indexOfComponent(win.containerPanel);
            if (idx != -1 && tabbedPane.getSelectedIndex() != idx) {
                tabbedPane.setTitleAt(idx, "🔒 " + sender + " (●)");
            }
        });
    }

    @Override
    public void onPrivateSent(String timestamp, String recipient, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(recipient, recipient, false);
            win.appendHtmlRow(buildOutgoingPrivateBubble(timestamp, message));
        });
    }

    @Override
    public void onSystemMessage(String timestamp, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow global = getOrCreateConversation(null, "Group Chat", false);
            global.appendHtmlRow(buildSystemNotice(message));
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
            }
        });
    }

    @Override
    public void onHistoryMessage(String timestamp, String sender, String receiver, String message) {
        SwingUtilities.invokeLater(() -> {
            String me = client.getCurrentUsername();
            boolean isMe = (me != null && sender.equalsIgnoreCase(me));

            if ("ALL".equalsIgnoreCase(receiver)) {
                ConversationWindow global = getOrCreateConversation(null, "Group Chat", false);
                global.appendHtmlRow(buildHistoryBubble(timestamp, sender, message, isMe));
            } else {
                String peer = isMe ? receiver : sender;
                ConversationWindow win = getOrCreateConversation(peer, peer, false);
                win.appendHtmlRow(buildHistoryBubble(timestamp, sender, message, isMe));
            }
        });
    }

    @Override
    public void onHistoryDone() {
        SwingUtilities.invokeLater(() -> {
            for (ConversationWindow win : conversations.values()) {
                win.appendHtmlRow(
                    "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin:10px 0;'>" +
                    "<tr><td align='center'>" +
                    "<font face='Segoe UI' size='2' color='#D97706'><b>──────── 📜 Historical Messages Restored from MySQL ────────</b></font>" +
                    "</td></tr></table>"
                );
            }
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            closeAllPoppedOutFrames();
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
