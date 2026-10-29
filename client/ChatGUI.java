package client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
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
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
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
 * ChatGUI provides a presentation-grade desktop intranet client for college staff
 * (JECRC StaffNet: College Staff Information & Circular Distribution System).
 * Demonstrates:
 *  - Department & Designation aware staff authentication
 *  - Categorized official circulars (Urgent, Meeting/MoM, Exam Cell, Lab, General)
 *  - Rich HTML/CSS institutional notice card rendering in Java Swing
 *  - Academic document dispatch (PDF orders, PPT lecture slides, DOCX duty rosters)
 *  - Per-staff confidential memo windows (JTabbedPane & Detachable Pop-Outs)
 *  - Event Dispatch Thread (EDT) safety via SwingUtilities.invokeLater
 *  - Zero external cloud dependencies (100% offline TCP/IP socket intranet)
 */
public class ChatGUI extends JFrame implements ChatClient.ChatEventListener {

    // Institutional Academic Color Palette
    private static final Color COLOR_PRIMARY = new Color(30, 58, 138);       // Academic Navy (Blue 900)
    private static final Color COLOR_PRIMARY_HOVER = new Color(29, 78, 216); // Blue 700
    private static final Color COLOR_NAVY_BAR = new Color(15, 23, 42);       // Slate 900
    private static final Color COLOR_BG_CANVAS = new Color(241, 245, 249);   // Slate 100
    private static final Color COLOR_BORDER = new Color(226, 232, 240);      // Slate 200
    private static final Color COLOR_TEXT_MAIN = new Color(30, 41, 59);      // Slate 800
    private static final Color COLOR_ONLINE_GREEN = new Color(16, 185, 129); // Emerald 500
    private static final Color COLOR_LOGOUT_RED = new Color(225, 29, 72);    // Rose 600
    private static final Color COLOR_GOLD_ACCENT = new Color(217, 119, 6);   // Amber 600

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
    private JComboBox<String> departmentCombo;
    private JComboBox<String> designationCombo;
    private JLabel authStatusLabel;
    private JButton loginBtn;
    private JButton registerBtn;

    // Chat View Components
    private JLabel currentUserLabel;
    private JLabel userStatusLabel;
    private JTabbedPane tabbedPane;
    private DefaultListModel<StaffItem> userListModel;
    private JList<StaffItem> userJList;
    private JLabel targetIndicator;
    private JComboBox<String> noticeCategoryCombo;
    private JTextField messageInputField;
    private JButton attachFileBtn;
    private JButton sendBtn;
    private JButton logoutBtn;
    private JLabel contactsCountLabel;

    private static final String NOTICE_BOARD_NAME = "📢 Faculty Notice Board & Circulars";
    private String selectedTarget = null; // null = Notice Board (Broadcast to all staff)

    // Map of active conversations: <Key, ConversationWindow>
    private final Map<String, ConversationWindow> conversations = new LinkedHashMap<>();

    // Staff directory cache for rich names
    private final Map<String, StaffItem> staffDirectory = new LinkedHashMap<>();

    // HTML Base Template for Notice Cards & Chat Bubbles
    private static final String HTML_HEADER = 
        "<html><head><style>" +
        "body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; margin: 8px 12px; background-color: #F8FAFC; }" +
        "</style></head><body>";
    private static final String HTML_FOOTER = "</body></html>";

    /**
     * Represents a staff member entry for the directory and dropdowns.
     */
    public static class StaffItem {
        public final String username;
        public final String department;
        public final String designation;
        public final boolean isNoticeBoard;

        public StaffItem(String username, String department, String designation, boolean isNoticeBoard) {
            this.username = username;
            this.department = department;
            this.designation = designation;
            this.isNoticeBoard = isNoticeBoard;
        }

        @Override
        public String toString() {
            if (isNoticeBoard) return NOTICE_BOARD_NAME;
            return username + " (" + designation + ")";
        }
    }

    public ChatGUI() {
        super("JECRC StaffNet — Faculty Information & Circular Distribution System");
        client.setListener(this);

        initSystemRendering();
        buildUI();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAllPoppedOutFrames();
                client.sendLogout();
                System.exit(0);
            }
        });

        setSize(1100, 780);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
    }

    private void initSystemRendering() {
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
    // 1. INSTITUTIONAL LOGIN & REGISTRATION PANEL
    // =========================================================================
    private JPanel createLoginPanel() {
        JPanel background = new JPanel(new GridBagLayout());
        background.setBackground(new Color(241, 245, 249));

        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(30, 40, 30, 40));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 8, 6, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Title Header
        JLabel headerIcon = new JLabel("🏛️ JECRC CAMPUS INTRANET", SwingConstants.CENTER);
        headerIcon.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerIcon.setForeground(COLOR_PRIMARY);
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        card.add(headerIcon, c);

        JLabel subHeader = new JLabel("Faculty & Staff Information & Circular Distribution System", SwingConstants.CENTER);
        subHeader.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subHeader.setForeground(new Color(100, 116, 139));
        c.gridy = 1;
        card.add(subHeader, c);

        // Server Host & Port
        JPanel serverRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        serverRow.setOpaque(false);
        serverRow.add(new JLabel("Host:"));
        hostField = new JTextField("localhost", 9);
        styleInputField(hostField);
        serverRow.add(hostField);

        serverRow.add(new JLabel("Port:"));
        portField = new JTextField(String.valueOf(ChatClient.class.getSimpleName().hashCode() > 0 ? "12345" : "12345"), 5);
        styleInputField(portField);
        serverRow.add(portField);

        c.gridy = 2;
        card.add(serverRow, c);

        // Username
        c.gridwidth = 1;
        c.gridy = 3; c.gridx = 0;
        card.add(createFieldLabel("Staff Username:"), c);
        usernameField = createStyledTextField("");
        c.gridx = 1;
        card.add(usernameField, c);

        // Password
        c.gridy = 4; c.gridx = 0;
        card.add(createFieldLabel("Password:"), c);
        passwordField = new JPasswordField();
        styleInputField(passwordField);
        c.gridx = 1;
        card.add(passwordField, c);

        // Department
        c.gridy = 5; c.gridx = 0;
        card.add(createFieldLabel("Department:"), c);
        departmentCombo = new JComboBox<>(new String[]{
            "Computer Science & AI",
            "AI & Data Science",
            "Information Technology",
            "Examination Cell",
            "AI & Computing Labs",
            "Principal Secretariat"
        });
        departmentCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        c.gridx = 1;
        card.add(departmentCombo, c);

        // Designation
        c.gridy = 6; c.gridx = 0;
        card.add(createFieldLabel("Designation:"), c);
        designationCombo = new JComboBox<>(new String[]{
            "Assistant Professor & Guide",
            "Head of Department (HOD)",
            "Associate Professor",
            "Assistant Professor",
            "Exam Cell Coordinator",
            "Senior Lab Incharge",
            "Administrative Officer"
        });
        designationCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        c.gridx = 1;
        card.add(designationCombo, c);

        // Status Label
        authStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        authStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        authStatusLabel.setForeground(COLOR_LOGOUT_RED);
        c.gridy = 7; c.gridx = 0; c.gridwidth = 2;
        card.add(authStatusLabel, c);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 5));
        btnPanel.setOpaque(false);

        loginBtn = createModernButton("Log In", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 130, 42);
        loginBtn.addActionListener(e -> attemptAuth(true));

        registerBtn = createModernButton("Register New Staff", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 180, 42);
        registerBtn.addActionListener(e -> attemptAuth(false));

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        c.gridy = 8;
        card.add(btnPanel, c);

        // Demo Tip
        JLabel tipLabel = new JLabel(
            "<html><center><font face='Segoe UI' size='2' color='#64748B'>" +
            "💡 <b>Default Staff Accounts</b> (Password: <b>admin123</b>):<br/>" +
            "<b>mona.nagar</b> (Guide) • <b>anand.gaur</b> (HOD) • <b>abhinav.batra</b> (Exam) • <b>amit.kumar</b> (Lab)" +
            "</font></center></html>", SwingConstants.CENTER
        );
        c.gridy = 9;
        card.add(tipLabel, c);

        passwordField.addActionListener(e -> attemptAuth(true));
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());

        background.add(card);
        return background;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lbl.setForeground(COLOR_TEXT_MAIN);
        return lbl;
    }

    private JTextField createStyledTextField(String text) {
        JTextField tf = new JTextField(text, 14);
        styleInputField(tf);
        return tf;
    }

    private void styleInputField(JTextField tf) {
        tf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tf.setPreferredSize(new Dimension(210, 36));
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
        String dept = (String) departmentCombo.getSelectedItem();
        String desig = (String) designationCombo.getSelectedItem();

        if (user.isEmpty() || pass.isEmpty()) {
            authStatusLabel.setText("Please enter both staff username and password.");
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
        authStatusLabel.setText(isLogin ? "Verifying staff credentials..." : "Registering staff account...");
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
                    client.sendRegister(user, pass, dept, desig);
                }
            } catch (IOException ex) {
                SwingUtilities.invokeLater(() -> {
                    authStatusLabel.setForeground(COLOR_LOGOUT_RED);
                    authStatusLabel.setText("Could not reach Campus Server at " + host + ":" + port);
                    loginBtn.setEnabled(true);
                    registerBtn.setEnabled(true);
                });
            }
        }).start();
    }

    // =========================================================================
    // 2. MAIN CHAT & NOTICE DASHBOARD
    // =========================================================================
    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(COLOR_BG_CANVAS);

        // --- Top Bar: Institutional Navy Header ---
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setBackground(COLOR_NAVY_BAR);
        topBar.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel userMetaPanel = new JPanel();
        userMetaPanel.setLayout(new BoxLayout(userMetaPanel, BoxLayout.Y_AXIS));
        userMetaPanel.setOpaque(false);

        JLabel brandHeader = new JLabel("🏛️ JECRC CAMPUS STAFFNET");
        brandHeader.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brandHeader.setForeground(Color.WHITE);

        currentUserLabel = new JLabel("Logged in as: Staff Member");
        currentUserLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        currentUserLabel.setForeground(new Color(226, 232, 240));

        userStatusLabel = new JLabel("● Online  •  Secure Campus Intranet (Port 12345)");
        userStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        userStatusLabel.setForeground(new Color(52, 211, 153));

        userMetaPanel.add(brandHeader);
        userMetaPanel.add(Box.createVerticalStrut(2));
        userMetaPanel.add(currentUserLabel);
        userMetaPanel.add(Box.createVerticalStrut(2));
        userMetaPanel.add(userStatusLabel);
        topBar.add(userMetaPanel, BorderLayout.WEST);

        // Right Actions: Downloads + Logout
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topActions.setOpaque(false);

        JButton openDownloadsBtn = createModernButton("📁 Staff Downloads", new Color(30, 41, 59), new Color(51, 65, 85), Color.WHITE, 160, 38);
        openDownloadsBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        openDownloadsBtn.addActionListener(e -> openDownloadsFolder());
        topActions.add(openDownloadsBtn);

        logoutBtn = createModernButton("Sign Out", new Color(30, 41, 59), COLOR_LOGOUT_RED, Color.WHITE, 110, 38);
        logoutBtn.addActionListener(e -> {
            closeAllPoppedOutFrames();
            client.sendLogout();
            cardLayout.show(mainContainer, VIEW_LOGIN);
            setTitle("JECRC StaffNet — Faculty Information & Circular Distribution System");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            authStatusLabel.setForeground(Color.GRAY);
            authStatusLabel.setText("Signed out successfully.");
        });
        topActions.add(logoutBtn);
        topBar.add(topActions, BorderLayout.EAST);
        panel.add(topBar, BorderLayout.NORTH);

        // --- Center: Multi-Tab Circular Window + Staff Directory ---
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBorder(new EmptyBorder(10, 14, 6, 14));
        centerContainer.setOpaque(false);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabbedPane.setBackground(Color.WHITE);

        tabbedPane.addChangeListener(e -> {
            int selectedIdx = tabbedPane.getSelectedIndex();
            if (selectedIdx != -1) {
                Component comp = tabbedPane.getComponentAt(selectedIdx);
                for (ConversationWindow win : conversations.values()) {
                    if (win.containerPanel == comp) {
                        selectedTarget = win.targetUser;
                        updateTargetControls(win.targetUser == null, win.displayName);
                        tabbedPane.setTitleAt(selectedIdx, win.getTabTitle());
                        break;
                    }
                }
            }
        });

        // Staff Directory Sidebar
        JPanel userListContainer = new JPanel(new BorderLayout(0, 8));
        userListContainer.setOpaque(false);

        JPanel userListHeader = new JPanel(new BorderLayout());
        userListHeader.setOpaque(false);
        JLabel contactsTitle = new JLabel("COLLEGE STAFF DIRECTORY");
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
        userJList.setBackground(Color.WHITE);
        userJList.setCellRenderer(new StaffDirectoryCellRenderer());

        userJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                StaffItem item = userJList.getSelectedValue();
                if (item == null || item.isNoticeBoard) {
                    getOrCreateConversation(null, "Notice Board", true);
                } else {
                    String me = client.getCurrentUsername();
                    if (me == null || !item.username.equalsIgnoreCase(me)) {
                        getOrCreateConversation(item.username, item.username + " (" + item.designation + ")", true);
                    }
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userJList);
        userScroll.setPreferredSize(new Dimension(280, 300));
        userScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1, true));
        userListContainer.add(userScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedPane, userListContainer);
        splitPane.setResizeWeight(0.74);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        centerContainer.add(splitPane, BorderLayout.CENTER);
        panel.add(centerContainer, BorderLayout.CENTER);

        // --- Bottom Area: Category Selector + Message Input + Attach File + Send ---
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 6));
        bottomContainer.setBorder(new EmptyBorder(4, 14, 10, 14));
        bottomContainer.setOpaque(false);

        // Channel indicator & Category Selector Row
        JPanel noticeControlRow = new JPanel(new BorderLayout(10, 0));
        noticeControlRow.setOpaque(false);

        targetIndicator = new JLabel("📢 Active Channel: Faculty Notice Board (Broadcast to All Staff)");
        targetIndicator.setFont(new Font("Segoe UI", Font.BOLD, 13));
        targetIndicator.setForeground(COLOR_PRIMARY);
        noticeControlRow.add(targetIndicator, BorderLayout.WEST);

        JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        categoryPanel.setOpaque(false);
        JLabel catLabel = new JLabel("Notice Category:");
        catLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        catLabel.setForeground(COLOR_TEXT_MAIN);
        categoryPanel.add(catLabel);

        noticeCategoryCombo = new JComboBox<>(new String[]{
            "📢 General Circular",
            "🚨 Urgent Directive",
            "📋 Meeting Notice / MoM",
            "📝 Exam Duty & Academic",
            "🔬 Lab & Infrastructure"
        });
        noticeCategoryCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        categoryPanel.add(noticeCategoryCombo);
        noticeControlRow.add(categoryPanel, BorderLayout.EAST);
        bottomContainer.add(noticeControlRow, BorderLayout.NORTH);

        // Input & Attachment Row
        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setOpaque(false);

        messageInputField = new JTextField();
        messageInputField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        messageInputField.setPreferredSize(new Dimension(messageInputField.getPreferredSize().width, 42));
        messageInputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
            BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        messageInputField.addActionListener(e -> sendMessage());

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightButtons.setOpaque(false);

        // Attach Academic Document Button (PDF, PPT, Word, etc.)
        attachFileBtn = createModernButton("📎 Attach Document", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 155, 42);
        attachFileBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        attachFileBtn.addActionListener(e -> selectAndSendFile());

        sendBtn = createModernButton("Post / Send", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 110, 42);
        sendBtn.addActionListener(e -> sendMessage());

        rightButtons.add(attachFileBtn);
        rightButtons.add(sendBtn);

        inputRow.add(messageInputField, BorderLayout.CENTER);
        inputRow.add(rightButtons, BorderLayout.EAST);
        bottomContainer.add(inputRow, BorderLayout.CENTER);

        // Telemetry Bar
        JPanel telemetryBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 2));
        telemetryBar.setOpaque(false);
        JLabel telemetryLabel = new JLabel(
            "<html><font face='Segoe UI' size='2' color='#64748B'>" +
            "🟢 <b>Campus Intranet</b>: TCP Port 12345  •  " +
            "⚡ <b>Multithreading</b>: ExecutorService  •  " +
            "📁 <b>Document Transfer</b>: Base64 Binary Stream  •  " +
            "🗄️ <b>Database</b>: MySQL 8.0 JDBC" +
            "</font></html>"
        );
        telemetryBar.add(telemetryLabel);
        bottomContainer.add(telemetryBar, BorderLayout.SOUTH);

        panel.add(bottomContainer, BorderLayout.SOUTH);
        return panel;
    }

    private void updateTargetControls(boolean isBroadcast, String name) {
        if (isBroadcast) {
            targetIndicator.setText("📢 Active Channel: Faculty Notice Board (Broadcast to All Staff)");
            targetIndicator.setForeground(COLOR_PRIMARY);
            noticeCategoryCombo.setEnabled(true);
            sendBtn.setText("Post Notice");
        } else {
            targetIndicator.setText("🔒 Confidential Faculty Memo with: " + name);
            targetIndicator.setForeground(new Color(124, 58, 237));
            noticeCategoryCombo.setEnabled(false);
            sendBtn.setText("Send Memo");
        }
    }

    // =========================================================================
    // 3. CONVERSATION WINDOW WITH RICH NOTICE CARDS
    // =========================================================================
    private class ConversationWindow {
        final String targetUser; // null for Notice Board
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
                ? "📢 Official College Notice Board (Broadcast to All Staff)"
                : "🔒 Confidential Faculty Memo: " + displayName;
            JLabel channelLabel = new JLabel(headerLabelText);
            channelLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            channelLabel.setForeground(COLOR_TEXT_MAIN);
            subHeader.add(channelLabel, BorderLayout.WEST);

            JPanel subActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            subActions.setOpaque(false);

            JButton popOutBtn = createModernButton("⧉ Pop Out Window", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 150, 28);
            popOutBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            popOutBtn.addActionListener(e -> togglePopOut(this));
            subActions.add(popOutBtn);

            subHeader.add(subActions, BorderLayout.EAST);
            containerPanel.add(subHeader, BorderLayout.NORTH);

            // JEditorPane configured for HTML/CSS institutional notice rendering
            chatEditor = new JEditorPane();
            chatEditor.setContentType("text/html");
            chatEditor.setEditable(false);
            chatEditor.setBackground(new Color(248, 250, 252));
            chatEditor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            chatEditor.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            chatEditor.setText(HTML_HEADER + HTML_FOOTER);

            scrollPane = new JScrollPane(chatEditor);
            scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1));
            containerPanel.add(scrollPane, BorderLayout.CENTER);
        }

        String getTabTitle() {
            return (targetUser == null) ? "📢 Notice Board" : "🔒 " + displayName;
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
            updateTargetControls(win.targetUser == null, win.displayName);
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

            JFrame frame = new JFrame("Staff Memo: " + win.displayName);
            frame.setSize(640, 560);
            frame.setLocationRelativeTo(this);
            frame.setLayout(new BorderLayout());

            JPanel popBottom = new JPanel(new BorderLayout(8, 0));
            popBottom.setBorder(new EmptyBorder(8, 10, 10, 10));
            JTextField popInput = new JTextField();
            popInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            popInput.setPreferredSize(new Dimension(200, 38));

            JPanel popRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            popRight.setOpaque(false);

            JButton popAttach = createModernButton("📎", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 50, 38);
            popAttach.addActionListener(e -> selectAndSendFile());

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

            popRight.add(popAttach);
            popRight.add(popSend);

            popBottom.add(popInput, BorderLayout.CENTER);
            popBottom.add(popRight, BorderLayout.EAST);

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
    // 4. ACADEMIC DOCUMENT SHARING (PDF, PPT, WORD, EXCEL)
    // =========================================================================
    private void selectAndSendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Academic Document to Dispatch (PDF, PPT, Word, Excel)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile == null || !selectedFile.exists()) return;

            // 25MB check
            if (selectedFile.length() > 25 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, "Document exceeds 25 MB limit.", "File Size Limit", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String targetName = (selectedTarget == null) 
                ? "Faculty Notice Board (All Staff)" 
                : selectedTarget;

            String confirmMsg = "Dispatch document:\n" + selectedFile.getName() + " (" + formatFileSize(selectedFile.length()) + ")\nto: " + targetName + "?";
            int confirm = JOptionPane.showConfirmDialog(this, confirmMsg, "Confirm Document Dispatch", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            new Thread(() -> {
                try {
                    byte[] data = Files.readAllBytes(selectedFile.toPath());
                    if (selectedTarget == null) {
                        client.sendFileBroadcast(selectedFile.getName(), data);
                    } else {
                        client.sendFilePrivate(selectedTarget, selectedFile.getName(), data);
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(ChatGUI.this, "Error reading file: " + ex.getMessage(), "Dispatch Error", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    }

    private File saveReceivedFile(String fileName, byte[] data) {
        File dir = new File("downloads");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File dest = new File(dir, fileName);
        int counter = 1;
        String base = fileName;
        String ext = "";
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            base = fileName.substring(0, dot);
            ext = fileName.substring(dot);
        }
        while (dest.exists()) {
            dest = new File(dir, base + "_" + counter + ext);
            counter++;
        }
        try {
            Files.write(dest.toPath(), data);
            return dest;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void openDownloadsFolder() {
        File dir = new File("downloads");
        if (!dir.exists()) dir.mkdirs();
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(dir);
            } else {
                JOptionPane.showMessageDialog(this, "Staff Downloads location: " + dir.getAbsolutePath());
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Staff Downloads location: " + dir.getAbsolutePath());
        }
    }

    private String getDocumentTypeBadge(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "📕 Official Order / Circular (PDF)";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "📊 Faculty Lecture / Presentation (PPT)";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "📘 Notice / Duty Roster / Minutes (DOCX)";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "📗 Grade Sheet / Attendance Register (XLSX)";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "🖼️ Academic Diagram / Photo (IMG)";
        if (lower.endsWith(".zip") || lower.endsWith(".rar")) return "📦 Document Archive (ZIP)";
        return "📁 Academic Document";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // =========================================================================
    // 5. HTML NOTICE CARD & CHAT BUBBLE RENDERING
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
     * Builds categorized circular cards for Notice Board broadcasts.
     */
    private String buildNoticeBoardBubble(String timestamp, String sender, String rawMessage, boolean isOutgoing) {
        String tag = "";
        String badge = "";
        String cardBg = isOutgoing ? "#EFF6FF" : "#FFFFFF";
        String border = isOutgoing ? "#BFDBFE" : "#E2E8F0";
        String content = rawMessage;

        if (rawMessage.startsWith("[URGENT]")) {
            tag = "URGENT";
            badge = "<span style='background-color:#DC2626; color:white; font-size:10px; font-weight:bold; padding:2px 8px; border-radius:4px;'>🚨 URGENT DIRECTIVE</span>";
            cardBg = "#FEF2F2";
            border = "#EF4444";
            content = rawMessage.substring(8).trim();
        } else if (rawMessage.startsWith("[MEETING]")) {
            tag = "MEETING";
            badge = "<span style='background-color:#D97706; color:white; font-size:10px; font-weight:bold; padding:2px 8px; border-radius:4px;'>📋 MEETING NOTICE / MoM</span>";
            cardBg = "#FFFBEB";
            border = "#F59E0B";
            content = rawMessage.substring(9).trim();
        } else if (rawMessage.startsWith("[EXAM]")) {
            tag = "EXAM";
            badge = "<span style='background-color:#2563EB; color:white; font-size:10px; font-weight:bold; padding:2px 8px; border-radius:4px;'>📝 EXAM CELL & ACADEMIC</span>";
            cardBg = "#EFF6FF";
            border = "#3B82F6";
            content = rawMessage.substring(6).trim();
        } else if (rawMessage.startsWith("[LAB]")) {
            tag = "LAB";
            badge = "<span style='background-color:#059669; color:white; font-size:10px; font-weight:bold; padding:2px 8px; border-radius:4px;'>🔬 LAB & INFRASTRUCTURE</span>";
            cardBg = "#ECFDF5";
            border = "#10B981";
            content = rawMessage.substring(5).trim();
        } else if (rawMessage.startsWith("[CIRCULAR]")) {
            tag = "CIRCULAR";
            badge = "<span style='background-color:#4F46E5; color:white; font-size:10px; font-weight:bold; padding:2px 8px; border-radius:4px;'>📢 OFFICIAL CIRCULAR</span>";
            cardBg = "#EEF2FF";
            border = "#6366F1";
            content = rawMessage.substring(10).trim();
        }

        String senderLabel = isOutgoing ? "You (Staff)" : sender;
        StaffItem staff = staffDirectory.get(sender.toLowerCase());
        if (staff != null && !isOutgoing) {
            senderLabel = staff.username + " [" + staff.designation + " - " + staff.department + "]";
        }

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:10px;'>" +
               "<tr><td>" +
               "<table width='100%' cellpadding='10' cellspacing='0' border='0' style='background-color:" + cardBg + "; border:1px solid " + border + "; border-left:5px solid " + border + ";'>" +
               "<tr><td>" +
               (badge.isEmpty() ? "" : badge + "&nbsp;&nbsp;") +
               "<font face='Segoe UI' size='3' color='#1E293B'><b>" + escapeHtml(senderLabel) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;•&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<div style='margin-top:6px;'><font face='Segoe UI' size='4' color='#0F172A'>" + escapeHtml(content) + "</font></div>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildIncomingPrivateBubble(String timestamp, String sender, String message) {
        StaffItem staff = staffDirectory.get(sender.toLowerCase());
        String senderLabel = (staff != null) 
            ? staff.username + " (" + staff.designation + ")" 
            : sender;

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='left'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#FAF5FF; border:1px solid #E9D5FF; border-left:4px solid #7C3AED;'>" +
               "<tr><td>" +
               "<font face='Segoe UI' size='3' color='#7C3AED'><b>🔒 Confidential Memo: " + escapeHtml(senderLabel) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<div style='margin-top:4px;'><font face='Segoe UI' size='4' color='#1E293B'>" + escapeHtml(message) + "</font></div>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildOutgoingPrivateBubble(String timestamp, String message) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:8px;'>" +
               "<tr><td align='right'>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#F0FDF4; border:1px solid #BBF7D0; border-right:4px solid #059669;'>" +
               "<tr><td align='right'>" +
               "<font face='Segoe UI' size='2' color='#0D9488'><b>🔒 Confidential Memo Sent</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<div style='margin-top:4px;' align='left'><font face='Segoe UI' size='4' color='#064E3B'>" + escapeHtml(message) + "</font></div>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildDocumentBubble(String timestamp, String sender, String fileName, long size, boolean isPrivate, boolean isOutgoing) {
        String docType = getDocumentTypeBadge(fileName);
        String sizeStr = formatFileSize(size);
        String cardBg = isOutgoing ? "#EFF6FF" : "#FFFFFF";
        String border = isOutgoing ? "#93C5FD" : "#CBD5E1";
        String titleTag = isOutgoing 
            ? "Dispatched by You" 
            : (isPrivate ? "🔒 Direct Dispatch: " + sender : "📢 Official Notice Board Attachment: " + sender);

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:10px;'>" +
               "<tr><td align='" + (isOutgoing ? "right" : "left") + "'>" +
               "<table cellpadding='10' cellspacing='0' border='0' style='background-color:" + cardBg + "; border:1px solid " + border + "; min-width:320px;'>" +
               "<tr><td>" +
               "<font face='Segoe UI' size='2' color='#2563EB'><b>" + escapeHtml(titleTag) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#F8FAFC; border:1px solid #E2E8F0; margin-top:6px; width:100%;'>" +
               "<tr><td>" +
               "<font face='Segoe UI' size='2' color='#475569'><b>" + escapeHtml(docType) + "</b></font><br>" +
               "<font face='Segoe UI' size='3' color='#0F172A'><b>" + escapeHtml(fileName) + "</b></font><br>" +
               "<font face='Segoe UI' size='2' color='#059669'><b>" + sizeStr + "</b> • Saved to downloads/ (Staff Archive)</font>" +
               "</td></tr></table>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildSystemNotice(String text) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin:6px 0;'>" +
               "<tr><td align='center'>" +
               "<font face='Segoe UI' size='2' color='#64748B'><i>— " + escapeHtml(text) + " —</i></font>" +
               "</td></tr></table>";
    }

    private void sendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty()) return;

        if (selectedTarget == null) {
            // Notice Board Broadcast with Category
            String cat = (String) noticeCategoryCombo.getSelectedItem();
            String prefix = "[CIRCULAR] ";
            if (cat != null) {
                if (cat.contains("Urgent")) prefix = "[URGENT] ";
                else if (cat.contains("Meeting")) prefix = "[MEETING] ";
                else if (cat.contains("Exam")) prefix = "[EXAM] ";
                else if (cat.contains("Lab")) prefix = "[LAB] ";
            }
            client.sendBroadcast(prefix + text);
        } else {
            // Confidential Private Memo
            client.sendPrivate(selectedTarget, text);
        }
        messageInputField.setText("");
    }

    // =========================================================================
    // 6. CLIENT LISTENER CALLBACKS (Swing EDT Safe)
    // =========================================================================
    @Override
    public void onLoginSuccess(String username, String department, String designation) {
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainContainer, VIEW_CHAT);
            currentUserLabel.setText("👤 " + username + "  •  " + designation + " (" + department + ")");
            setTitle("JECRC StaffNet — " + username + " [" + designation + "]");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
            getOrCreateConversation(null, "Notice Board", true);
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
            authStatusLabel.setForeground(new Color(5, 150, 105));
            authStatusLabel.setText(message);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
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
            ConversationWindow win = getOrCreateConversation(null, "Notice Board", false);
            boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());
            win.appendHtmlRow(buildNoticeBoardBubble(timestamp, sender, message, isMe));
        });
    }

    @Override
    public void onPrivateReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(sender, sender, false);
            win.appendHtmlRow(buildIncomingPrivateBubble(timestamp, sender, message));
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
            ConversationWindow win = getOrCreateConversation(null, "Notice Board", false);
            win.appendHtmlRow(buildSystemNotice(message));
        });
    }

    @Override
    public void onUserListUpdated(List<String> rawUsers) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            staffDirectory.clear();

            // Always add Notice Board at top
            StaffItem noticeBoard = new StaffItem("ALL", "College-wide", "Notice Board", true);
            userListModel.addElement(noticeBoard);

            int count = 0;
            for (String raw : rawUsers) {
                if (raw == null || raw.trim().isEmpty()) continue;
                String[] parts = raw.split(":");
                String u = parts[0].trim();
                String dep = (parts.length >= 2) ? parts[1].trim() : "General";
                String des = (parts.length >= 3) ? parts[2].trim() : "Faculty";

                StaffItem staff = new StaffItem(u, dep, des, false);
                staffDirectory.put(u.toLowerCase(), staff);
                userListModel.addElement(staff);
                count++;
            }
            contactsCountLabel.setText(count + " staff active");
        });
    }

    @Override
    public void onHistoryMessage(String timestamp, String sender, String receiver, String message) {
        SwingUtilities.invokeLater(() -> {
            if ("ALL".equalsIgnoreCase(receiver)) {
                ConversationWindow win = getOrCreateConversation(null, "Notice Board", false);
                boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());
                win.appendHtmlRow(buildNoticeBoardBubble(timestamp, sender, message, isMe));
            } else {
                String partner = sender.equalsIgnoreCase(client.getCurrentUsername()) ? receiver : sender;
                ConversationWindow win = getOrCreateConversation(partner, partner, false);
                if (sender.equalsIgnoreCase(client.getCurrentUsername())) {
                    win.appendHtmlRow(buildOutgoingPrivateBubble(timestamp, message));
                } else {
                    win.appendHtmlRow(buildIncomingPrivateBubble(timestamp, sender, message));
                }
            }
        });
    }

    @Override
    public void onHistoryDone() {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(null, "Notice Board", false);
            win.appendHtmlRow(buildSystemNotice("Staff history synchronized with MySQL database"));
        });
    }

    @Override
    public void onFileBroadcastReceived(String timestamp, String sender, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;
        boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(null, "Notice Board", false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, sender, fileName, size, false, isMe));
        });
    }

    @Override
    public void onFilePrivateReceived(String timestamp, String sender, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(sender, sender, false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, sender, fileName, size, true, false));
        });
    }

    @Override
    public void onFilePrivateSent(String timestamp, String recipient, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(recipient, recipient, false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, recipient, fileName, size, true, true));
        });
    }

    @Override
    public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            closeAllPoppedOutFrames();
            cardLayout.show(mainContainer, VIEW_LOGIN);
            authStatusLabel.setForeground(COLOR_LOGOUT_RED);
            authStatusLabel.setText("Disconnected from server: " + reason);
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);
        });
    }

    // =========================================================================
    // 7. CUSTOM SWING HELPERS & RENDERERS
    // =========================================================================
    private JButton createModernButton(String text, Color bg, Color hoverBg, Color fg, int width, int height) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? hoverBg : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(fg);
        btn.setPreferredSize(new Dimension(width, height));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /**
     * Custom ListCellRenderer for the College Staff Directory sidebar.
     */
    private class StaffDirectoryCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(6, 2));
            p.setBorder(new EmptyBorder(6, 10, 6, 10));

            if (isSelected) {
                p.setBackground(new Color(238, 242, 255));
            } else {
                p.setBackground(Color.WHITE);
            }

            if (value instanceof StaffItem) {
                StaffItem item = (StaffItem) value;
                if (item.isNoticeBoard) {
                    JLabel title = new JLabel("📢 Faculty Notice Board");
                    title.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    title.setForeground(COLOR_PRIMARY);

                    JLabel sub = new JLabel("All Departments & Faculty");
                    sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    sub.setForeground(new Color(100, 116, 139));

                    JPanel col = new JPanel();
                    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
                    col.setOpaque(false);
                    col.add(title);
                    col.add(sub);
                    p.add(col, BorderLayout.CENTER);
                } else {
                    String me = client.getCurrentUsername();
                    boolean isMe = (me != null && item.username.equalsIgnoreCase(me));
                    String label = "● " + item.username + (isMe ? " (You)" : "");

                    JLabel name = new JLabel(label);
                    name.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    name.setForeground(isMe ? COLOR_PRIMARY : COLOR_TEXT_MAIN);

                    JLabel meta = new JLabel(item.designation + " • " + item.department);
                    meta.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    meta.setForeground(new Color(100, 116, 139));

                    JPanel col = new JPanel();
                    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
                    col.setOpaque(false);
                    col.add(name);
                    col.add(meta);
                    p.add(col, BorderLayout.CENTER);
                }
            }
            return p;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new ChatGUI().setVisible(true);
        });
    }
}
