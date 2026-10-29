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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
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
 * ChatGUI provides an institutional desktop intranet client for college staff
 * (JECRC StaffNet: College Staff Information & Circular Distribution System).
 * Demonstrates:
 *  - Vector-rendered AppIcons preventing any missing font glyph ("tofu") artifacts
 *  - Separate Department Notice Boards: Isolated by department so only departmental faculty can message & view
 *  - Central Main Notice Board: Campus-wide, visible to all departments, with posting restricted to Principal & HODs
 *  - Confidential Faculty Memos (1-to-1) with Pop-Out separate window support
 *  - Categorized notices (Urgent, Meeting/MoM, Exam Cell, Lab, General Circular)
 *  - Zero-cloud academic document sharing (PDF orders, PPT lecture slides, DOCX duty rosters)
 *  - Rich CSS & HTML card rendering for presentation-grade visual clarity
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

    private static final String VIEW_LOGIN = "LOGIN_VIEW";
    private static final String VIEW_CHAT = "CHAT_VIEW";

    public static final String KEY_MAIN_BOARD = "MAIN";
    public static final String KEY_DEPT_BOARD = "DEPT";

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
    private DefaultListModel<DirectoryEntry> userListModel;
    private JList<DirectoryEntry> userJList;
    private JLabel targetIndicator;
    private JComboBox<String> noticeCategoryCombo;
    private JTextField messageInputField;
    private JButton attachFileBtn;
    private JButton sendBtn;
    private JButton logoutBtn;
    private JLabel contactsCountLabel;

    // Active selected channel: "MAIN", "DEPT", or username
    private String selectedTarget = KEY_MAIN_BOARD;

    // Conversations map: <Key, ConversationWindow>
    private final Map<String, ConversationWindow> conversations = new LinkedHashMap<>();

    // Staff directory cache: <username_lower, DirectoryEntry>
    private final Map<String, DirectoryEntry> staffDirectory = new LinkedHashMap<>();

    // HTML/CSS Base Template for Notice Cards & Chat Bubbles
    private static final String HTML_HEADER = 
        "<html><head><style>" +
        "body { font-family: 'Segoe UI', -apple-system, Tahoma, Geneva, Verdana, sans-serif; margin: 10px 14px; background-color: #F8FAFC; color: #1E293B; }" +
        "</style></head><body>";
    private static final String HTML_FOOTER = "</body></html>";

    /**
     * Directory entry representing Notice Boards or Staff Members in the sidebar list.
     */
    public static class DirectoryEntry {
        public final String key; // "MAIN", "DEPT", or username
        public final String title;
        public final String department;
        public final String designation;
        public final int type; // 0 = Main Board, 1 = Dept Board, 2 = Staff User

        public DirectoryEntry(String key, String title, String department, String designation, int type) {
            this.key = key;
            this.title = title;
            this.department = department;
            this.designation = designation;
            this.type = type;
        }

        @Override
        public String toString() {
            return title;
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

        setSize(1140, 790);
        setMinimumSize(new Dimension(980, 660));
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

    private boolean isCurrentUserAuthority() {
        String des = client.getCurrentDesignation();
        if (des == null) return false;
        String d = des.trim().toLowerCase();
        return d.contains("principal") || d.contains("hod") || d.contains("head of department");
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
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(203, 213, 225));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(32, 44, 32, 44));

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 10, 6, 10);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Institutional Crest Header
        JLabel instName = new JLabel("JAIPUR ENGINEERING COLLEGE & RESEARCH CENTRE", SwingConstants.CENTER);
        instName.setFont(new Font("Segoe UI", Font.BOLD, 12));
        instName.setForeground(new Color(100, 116, 139));
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
        card.add(instName, c);

        JLabel headerTitle = new JLabel("JECRC CAMPUS STAFFNET", SwingConstants.CENTER);
        headerTitle.setIcon(AppIcons.MAIN_BOARD_20);
        headerTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerTitle.setForeground(COLOR_PRIMARY);
        c.gridy = 1;
        card.add(headerTitle, c);

        JLabel subHeader = new JLabel("Faculty Information & Official Circular Distribution System", SwingConstants.CENTER);
        subHeader.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subHeader.setForeground(new Color(71, 85, 105));
        c.gridy = 2;
        card.add(subHeader, c);

        // Server Host & Port Row
        JPanel serverRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        serverRow.setOpaque(false);
        serverRow.add(new JLabel("Host:"));
        hostField = new JTextField("localhost", 9);
        styleInputField(hostField);
        serverRow.add(hostField);

        serverRow.add(new JLabel("Port:"));
        portField = new JTextField("12345", 5);
        styleInputField(portField);
        serverRow.add(portField);

        c.gridy = 3;
        c.insets = new Insets(10, 10, 12, 10);
        card.add(serverRow, c);

        c.insets = new Insets(5, 10, 5, 10);

        // Staff Username
        c.gridwidth = 1;
        c.gridy = 4; c.gridx = 0;
        card.add(createFieldLabel("Staff Username:"), c);
        usernameField = createStyledTextField("");
        c.gridx = 1;
        card.add(usernameField, c);

        // Password
        c.gridy = 5; c.gridx = 0;
        card.add(createFieldLabel("Password:"), c);
        passwordField = new JPasswordField();
        styleInputField(passwordField);
        c.gridx = 1;
        card.add(passwordField, c);

        // Department Selector (Filtered)
        c.gridy = 6; c.gridx = 0;
        card.add(createFieldLabel("Department:"), c);
        departmentCombo = new JComboBox<>(new String[]{
            "Computer Science & AI",
            "Information Technology",
            "AI & Data Science",
            "Examination Cell",
            "Administration"
        });
        departmentCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        departmentCombo.setPreferredSize(new Dimension(210, 36));
        c.gridx = 1;
        card.add(departmentCombo, c);

        // Designation Selector
        c.gridy = 7; c.gridx = 0;
        card.add(createFieldLabel("Designation:"), c);
        designationCombo = new JComboBox<>(new String[]{
            "Principal",
            "Head of Department (HOD)",
            "Professor",
            "Associate Professor",
            "Assistant Professor",
            "Exam Cell Coordinator",
            "Senior Lab Incharge",
            "Administrative Officer"
        });
        designationCombo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        designationCombo.setPreferredSize(new Dimension(210, 36));
        c.gridx = 1;
        card.add(designationCombo, c);

        // Status Feedback Label
        authStatusLabel = new JLabel(" ", SwingConstants.CENTER);
        authStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        authStatusLabel.setForeground(COLOR_LOGOUT_RED);
        c.gridy = 8; c.gridx = 0; c.gridwidth = 2;
        card.add(authStatusLabel, c);

        // Buttons Row
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 6));
        btnPanel.setOpaque(false);

        loginBtn = createModernButton("Log In", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 135, 42);
        loginBtn.addActionListener(e -> attemptAuth(true));

        registerBtn = createModernButton("Register New Staff", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 185, 42);
        registerBtn.addActionListener(e -> attemptAuth(false));

        btnPanel.add(loginBtn);
        btnPanel.add(registerBtn);

        c.gridy = 9;
        card.add(btnPanel, c);

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

        // Top Navigation Bar
        JPanel topBar = new JPanel(new BorderLayout(15, 0));
        topBar.setBackground(COLOR_NAVY_BAR);
        topBar.setBorder(new EmptyBorder(12, 20, 12, 20));

        JPanel userMetaPanel = new JPanel();
        userMetaPanel.setLayout(new BoxLayout(userMetaPanel, BoxLayout.Y_AXIS));
        userMetaPanel.setOpaque(false);

        JLabel brandHeader = new JLabel("JECRC CAMPUS STAFFNET");
        brandHeader.setIcon(AppIcons.MAIN_BOARD_20);
        brandHeader.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brandHeader.setForeground(Color.WHITE);

        currentUserLabel = new JLabel("Logged in as: Staff Member");
        currentUserLabel.setIcon(AppIcons.USER_16);
        currentUserLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        currentUserLabel.setForeground(new Color(226, 232, 240));

        userStatusLabel = new JLabel("Online  •  Secure Campus Intranet (Port 12345)");
        userStatusLabel.setIcon(AppIcons.DOT_ONLINE);
        userStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        userStatusLabel.setForeground(new Color(52, 211, 153));

        userMetaPanel.add(brandHeader);
        userMetaPanel.add(Box.createVerticalStrut(3));
        userMetaPanel.add(currentUserLabel);
        userMetaPanel.add(Box.createVerticalStrut(2));
        userMetaPanel.add(userStatusLabel);
        topBar.add(userMetaPanel, BorderLayout.WEST);

        // Right Actions: Downloads + Sign Out
        JPanel topActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topActions.setOpaque(false);

        JButton openDownloadsBtn = createModernButton("Staff Downloads", new Color(30, 41, 59), new Color(51, 65, 85), Color.WHITE, 160, 38);
        openDownloadsBtn.setIcon(AppIcons.FOLDER_16);
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

        // Center: Tabbed Window System + Staff Directory
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
                        selectedTarget = win.targetKey;
                        updateChannelControls();
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
        contactsCountLabel.setIcon(AppIcons.DOT_ONLINE);
        contactsCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        contactsCountLabel.setForeground(COLOR_ONLINE_GREEN);

        userListHeader.add(contactsTitle, BorderLayout.WEST);
        userListHeader.add(contactsCountLabel, BorderLayout.EAST);
        userListContainer.add(userListHeader, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userJList = new JList<>(userListModel);
        userJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userJList.setBackground(Color.WHITE);
        userJList.setCellRenderer(new DirectoryCellRenderer());

        userJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DirectoryEntry entry = userJList.getSelectedValue();
                if (entry != null) {
                    if (entry.key.equals(KEY_MAIN_BOARD)) {
                        switchToTab(KEY_MAIN_BOARD);
                    } else if (entry.key.equals(KEY_DEPT_BOARD)) {
                        switchToTab(KEY_DEPT_BOARD);
                    } else {
                        String me = client.getCurrentUsername();
                        if (me == null || !entry.key.equalsIgnoreCase(me)) {
                            getOrCreateConversation(entry.key, entry.title, true);
                        }
                    }
                }
            }
        });

        JScrollPane userScroll = new JScrollPane(userJList);
        userScroll.setPreferredSize(new Dimension(300, 300));
        userScroll.setBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1, true));
        userListContainer.add(userScroll, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedPane, userListContainer);
        splitPane.setResizeWeight(0.72);
        splitPane.setBorder(null);
        splitPane.setDividerSize(8);
        centerContainer.add(splitPane, BorderLayout.CENTER);
        panel.add(centerContainer, BorderLayout.CENTER);

        // Bottom Controls: Channel indicator + Category + Input + Buttons
        JPanel bottomContainer = new JPanel(new BorderLayout(0, 6));
        bottomContainer.setBorder(new EmptyBorder(4, 14, 10, 14));
        bottomContainer.setOpaque(false);

        JPanel noticeControlRow = new JPanel(new BorderLayout(10, 0));
        noticeControlRow.setOpaque(false);

        targetIndicator = new JLabel("Active Channel: Main Campus Notice Board");
        targetIndicator.setIcon(AppIcons.MAIN_BOARD_16);
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
            "General Circular",
            "Urgent Directive",
            "Meeting Notice / MoM",
            "Exam Duty & Academic",
            "Lab & Infrastructure"
        });
        noticeCategoryCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        categoryPanel.add(noticeCategoryCombo);
        noticeControlRow.add(categoryPanel, BorderLayout.EAST);
        bottomContainer.add(noticeControlRow, BorderLayout.NORTH);

        // Input & Action Buttons
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

        attachFileBtn = createModernButton("Attach Document", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 155, 42);
        attachFileBtn.setIcon(AppIcons.ATTACH_16);
        attachFileBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        attachFileBtn.addActionListener(e -> selectAndSendFile());

        sendBtn = createModernButton("Post Notice", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 125, 42);
        sendBtn.addActionListener(e -> sendMessage());

        rightButtons.add(attachFileBtn);
        rightButtons.add(sendBtn);

        inputRow.add(messageInputField, BorderLayout.CENTER);
        inputRow.add(rightButtons, BorderLayout.EAST);
        bottomContainer.add(inputRow, BorderLayout.CENTER);

        // Live Technical Telemetry Bar
        JPanel telemetryBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 2));
        telemetryBar.setOpaque(false);
        JLabel telemetryLabel = new JLabel(
            "<html><font face='Segoe UI' size='2' color='#64748B'>" +
            "🟢 <b>Campus Intranet</b>: TCP Port 12345  •  " +
            "🏛️ <b>Main Notice Board</b>: Principal & HODs  •  " +
            "📢 <b>Department Board</b>: Isolated by Dept  •  " +
            "🗄️ <b>Database</b>: MySQL 8.0 JDBC" +
            "</font></html>"
        );
        telemetryBar.add(telemetryLabel);
        bottomContainer.add(telemetryBar, BorderLayout.SOUTH);

        panel.add(bottomContainer, BorderLayout.SOUTH);
        return panel;
    }

    private void switchToTab(String key) {
        ConversationWindow win = conversations.get(key.toLowerCase());
        if (win != null && !win.isPoppedOut) {
            tabbedPane.setSelectedComponent(win.containerPanel);
            selectedTarget = win.targetKey;
            updateChannelControls();
        }
    }

    private void updateChannelControls() {
        if (KEY_MAIN_BOARD.equals(selectedTarget)) {
            boolean isAuth = isCurrentUserAuthority();
            if (isAuth) {
                targetIndicator.setText("Active Channel: Main Campus Notice Board (Broadcasting to All Departments)");
                targetIndicator.setIcon(AppIcons.MAIN_BOARD_16);
                targetIndicator.setForeground(COLOR_PRIMARY);
                messageInputField.setEnabled(true);
                messageInputField.setToolTipText("Enter campus-wide circular announcement...");
                noticeCategoryCombo.setEnabled(true);
                sendBtn.setEnabled(true);
                sendBtn.setText("Post Campus Notice");
                attachFileBtn.setEnabled(true);
            } else {
                targetIndicator.setText("Main Campus Notice Board (Read-Only: Principal & HOD Announcements)");
                targetIndicator.setIcon(AppIcons.LOCK_16);
                targetIndicator.setForeground(new Color(100, 116, 139));
                messageInputField.setEnabled(false);
                messageInputField.setText("");
                messageInputField.setToolTipText("Read-only: Only Principal and HODs can broadcast on the Main Notice Board.");
                noticeCategoryCombo.setEnabled(false);
                sendBtn.setEnabled(false);
                sendBtn.setText("Read-Only");
                attachFileBtn.setEnabled(false);
            }
        } else if (KEY_DEPT_BOARD.equals(selectedTarget)) {
            String dept = client.getCurrentDepartment();
            targetIndicator.setText("Active Channel: " + dept + " Department Notice Board (Department Faculty Only)");
            targetIndicator.setIcon(AppIcons.DEPT_BOARD_16);
            targetIndicator.setForeground(COLOR_PRIMARY);
            messageInputField.setEnabled(true);
            messageInputField.setToolTipText("Enter notice for " + dept + " department...");
            noticeCategoryCombo.setEnabled(true);
            sendBtn.setEnabled(true);
            sendBtn.setText("Post Dept Notice");
            attachFileBtn.setEnabled(true);
        } else {
            // Private Memo
            targetIndicator.setText("Confidential Faculty Memo with: " + selectedTarget);
            targetIndicator.setIcon(AppIcons.LOCK_16);
            targetIndicator.setForeground(new Color(124, 58, 237));
            messageInputField.setEnabled(true);
            messageInputField.setToolTipText("Enter confidential memo...");
            noticeCategoryCombo.setEnabled(false);
            sendBtn.setEnabled(true);
            sendBtn.setText("Send Memo");
            attachFileBtn.setEnabled(true);
        }
    }

    // =========================================================================
    // 3. CONVERSATION WINDOW WITH VECTOR TAB ICONS
    // =========================================================================
    private class ConversationWindow {
        final String targetKey; // "MAIN", "DEPT", or username
        final String displayName;
        final JPanel containerPanel;
        final JEditorPane chatEditor;
        final JScrollPane scrollPane;
        final StringBuilder htmlBuffer = new StringBuilder();
        JFrame popOutFrame;
        boolean isPoppedOut = false;

        ConversationWindow(String targetKey, String displayName, String headerText) {
            this.targetKey = targetKey;
            this.displayName = displayName;

            containerPanel = new JPanel(new BorderLayout(0, 0));
            containerPanel.setBackground(Color.WHITE);

            JPanel subHeader = new JPanel(new BorderLayout());
            subHeader.setBackground(new Color(248, 250, 252));
            subHeader.setBorder(new EmptyBorder(8, 14, 8, 14));

            JLabel channelLabel = new JLabel(headerText);
            channelLabel.setIcon(getTabIcon());
            channelLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            channelLabel.setForeground(COLOR_TEXT_MAIN);
            subHeader.add(channelLabel, BorderLayout.WEST);

            if (!KEY_MAIN_BOARD.equals(targetKey) && !KEY_DEPT_BOARD.equals(targetKey)) {
                JPanel subActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
                subActions.setOpaque(false);
                JButton popOutBtn = createModernButton("Pop Out Window", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 150, 28);
                popOutBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                popOutBtn.addActionListener(e -> togglePopOut(this));
                subActions.add(popOutBtn);
                subHeader.add(subActions, BorderLayout.EAST);
            }

            containerPanel.add(subHeader, BorderLayout.NORTH);

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

        Icon getTabIcon() {
            if (KEY_MAIN_BOARD.equals(targetKey)) return AppIcons.MAIN_BOARD_16;
            if (KEY_DEPT_BOARD.equals(targetKey)) return AppIcons.DEPT_BOARD_16;
            return AppIcons.LOCK_16;
        }

        String getTabTitle() {
            if (KEY_MAIN_BOARD.equals(targetKey)) return "Main Notice Board";
            if (KEY_DEPT_BOARD.equals(targetKey)) return client.getCurrentDepartment();
            return displayName;
        }

        void appendHtmlRow(String rowHtml) {
            htmlBuffer.append(rowHtml);
            chatEditor.setText(HTML_HEADER + htmlBuffer.toString() + HTML_FOOTER);
            SwingUtilities.invokeLater(() -> {
                chatEditor.setCaretPosition(chatEditor.getDocument().getLength());
            });
        }
    }

    private synchronized ConversationWindow getOrCreateConversation(String targetKey, String displayName, boolean select) {
        String key = targetKey.toLowerCase();
        ConversationWindow win = conversations.get(key);

        if (win == null) {
            String headerText;
            if (KEY_MAIN_BOARD.equalsIgnoreCase(targetKey)) {
                headerText = "Central College Notice Board (Principal & HOD Broadcasts to All Departments)";
            } else if (KEY_DEPT_BOARD.equalsIgnoreCase(targetKey)) {
                headerText = client.getCurrentDepartment() + " Department Notice Board (Department Faculty Only)";
            } else {
                headerText = "Confidential Faculty Memo: " + displayName;
            }

            win = new ConversationWindow(targetKey, displayName, headerText);
            conversations.put(key, win);
            tabbedPane.addTab(win.getTabTitle(), win.getTabIcon(), win.containerPanel);
        }

        if (select && !win.isPoppedOut) {
            tabbedPane.setSelectedComponent(win.containerPanel);
            selectedTarget = win.targetKey;
            updateChannelControls();
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

            JButton popAttach = createModernButton("Attach", new Color(241, 245, 249), new Color(226, 232, 240), COLOR_TEXT_MAIN, 75, 38);
            popAttach.setIcon(AppIcons.ATTACH_16);
            popAttach.addActionListener(e -> selectAndSendFile());

            JButton popSend = createModernButton("Send", COLOR_PRIMARY, COLOR_PRIMARY_HOVER, Color.WHITE, 80, 38);
            Runnable sendAction = () -> {
                String text = popInput.getText().trim();
                if (!text.isEmpty()) {
                    client.sendPrivate(win.targetKey, text);
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
                    tabbedPane.addTab(win.getTabTitle(), win.getTabIcon(), win.containerPanel);
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
            tabbedPane.addTab(win.getTabTitle(), win.getTabIcon(), win.containerPanel);
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
    // 4. ACADEMIC DOCUMENT SHARING
    // =========================================================================
    private void selectAndSendFile() {
        if (KEY_MAIN_BOARD.equals(selectedTarget) && !isCurrentUserAuthority()) {
            JOptionPane.showMessageDialog(this, "Only Principal and HODs can attach documents on the Main Notice Board.", "Permission Denied", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Academic Document to Dispatch (PDF, PPT, Word, Excel)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile == null || !selectedFile.exists()) return;

            if (selectedFile.length() > 25 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, "Document exceeds 25 MB limit.", "File Size Limit", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String targetName = KEY_MAIN_BOARD.equals(selectedTarget)
                ? "Main Campus Notice Board (All Departments)"
                : (KEY_DEPT_BOARD.equals(selectedTarget) ? client.getCurrentDepartment() + " Department Notice Board" : selectedTarget);

            String confirmMsg = "Dispatch document:\n" + selectedFile.getName() + " (" + formatFileSize(selectedFile.length()) + ")\nto: " + targetName + "?";
            int confirm = JOptionPane.showConfirmDialog(this, confirmMsg, "Confirm Document Dispatch", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            new Thread(() -> {
                try {
                    byte[] data = Files.readAllBytes(selectedFile.toPath());
                    if (KEY_MAIN_BOARD.equals(selectedTarget)) {
                        client.sendFileBroadcastMain(selectedFile.getName(), data);
                    } else if (KEY_DEPT_BOARD.equals(selectedTarget)) {
                        client.sendFileBroadcastDept(selectedFile.getName(), data);
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
        if (!dir.exists()) dir.mkdirs();
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
        if (lower.endsWith(".pdf")) return "PDF Circular / Order";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "Faculty Presentation / Lecture Slides";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "Duty Roster / Minutes of Meeting";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "Grade Sheet / Attendance Register";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "Academic Diagram / Image";
        if (lower.endsWith(".zip") || lower.endsWith(".rar")) return "Document Archive (ZIP)";
        return "Academic Document";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    // =========================================================================
    // 5. CSS & HTML NOTICE CARD RENDERING (CLEAN WEB-SAFE BADGES)
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
     * Builds modern, CSS-styled institutional notice cards with color-coded accent bars.
     * Uses crisp, standard web-safe text badges to ensure zero tofu boxes on any display.
     */
    private String buildNoticeBubble(String timestamp, String sender, String rawMessage, boolean isOutgoing, String boardName) {
        String badge = "";
        String cardBg = isOutgoing ? "#F8FAFC" : "#FFFFFF";
        String border = "#E2E8F0";
        String accentColor = "#4F46E5"; // Default Indigo
        String content = rawMessage;

        if (rawMessage.startsWith("[URGENT]")) {
            accentColor = "#DC2626"; // Crimson
            badge = "<span style='background-color:#DC2626; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:3px 9px;'>URGENT DIRECTIVE</span>";
            cardBg = "#FFF5F5";
            border = "#FECACA";
            content = rawMessage.substring(8).trim();
        } else if (rawMessage.startsWith("[MEETING]")) {
            accentColor = "#D97706"; // Amber
            badge = "<span style='background-color:#D97706; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:3px 9px;'>MEETING NOTICE / MoM</span>";
            cardBg = "#FFFBEB";
            border = "#FDE68A";
            content = rawMessage.substring(9).trim();
        } else if (rawMessage.startsWith("[EXAM]")) {
            accentColor = "#2563EB"; // Royal Blue
            badge = "<span style='background-color:#2563EB; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:3px 9px;'>EXAM CELL & ACADEMIC</span>";
            cardBg = "#F0F9FF";
            border = "#BAE6FD";
            content = rawMessage.substring(6).trim();
        } else if (rawMessage.startsWith("[LAB]")) {
            accentColor = "#059669"; // Emerald
            badge = "<span style='background-color:#059669; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:3px 9px;'>LAB & INFRASTRUCTURE</span>";
            cardBg = "#F0FDF4";
            border = "#BBF7D0";
            content = rawMessage.substring(5).trim();
        } else if (rawMessage.startsWith("[CIRCULAR]")) {
            accentColor = "#4F46E5"; // Indigo
            badge = "<span style='background-color:#4F46E5; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:3px 9px;'>OFFICIAL CIRCULAR</span>";
            cardBg = "#F5F3FF";
            border = "#DDD6FE";
            content = rawMessage.substring(10).trim();
        }

        String senderLabel = isOutgoing ? "You (" + client.getCurrentDesignation() + ")" : sender;
        DirectoryEntry staff = staffDirectory.get(sender.toLowerCase());
        if (staff != null && !isOutgoing) {
            senderLabel = staff.key + " [" + staff.designation + " • " + staff.department + "]";
        }

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:12px;'>" +
               "<tr><td>" +
               "<table width='100%' cellpadding='12' cellspacing='0' border='0' style='background-color:" + cardBg + "; border:1px solid " + border + "; border-left:6px solid " + accentColor + ";'>" +
               "<tr><td>" +
               (badge.isEmpty() ? "" : badge + "&nbsp;&nbsp;") +
               "<span style='color:#64748B; font-size:11px; font-weight:600;'>[ " + escapeHtml(boardName) + " ]</span><br/>" +
               "<div style='margin-top:6px;'>" +
               "<font face='Segoe UI' size='3' color='#0F172A'><b>" + escapeHtml(senderLabel) + "</b></font>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;•&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font>" +
               "</div>" +
               "<div style='margin-top:8px; padding-top:6px; border-top:1px solid " + border + ";'>" +
               "<font face='Segoe UI' size='4' color='#1E293B'>" + escapeHtml(content) + "</font>" +
               "</div>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildIncomingPrivateBubble(String timestamp, String sender, String message) {
        DirectoryEntry staff = staffDirectory.get(sender.toLowerCase());
        String senderLabel = (staff != null) 
            ? staff.key + " (" + staff.designation + " • " + staff.department + ")" 
            : sender;

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:10px;'>" +
               "<tr><td align='left'>" +
               "<table cellpadding='10' cellspacing='0' border='0' style='background-color:#FAF5FF; border:1px solid #E9D5FF; border-left:6px solid #9333EA; min-width:320px;'>" +
               "<tr><td>" +
               "<span style='background-color:#9333EA; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:2px 8px;'>CONFIDENTIAL MEMO</span>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;•&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br/>" +
               "<div style='margin-top:4px;'><font face='Segoe UI' size='3' color='#6B21A8'><b>" + escapeHtml(senderLabel) + "</b></font></div>" +
               "<div style='margin-top:6px; padding-top:6px; border-top:1px solid #E9D5FF;'><font face='Segoe UI' size='4' color='#1E293B'>" + escapeHtml(message) + "</font></div>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildOutgoingPrivateBubble(String timestamp, String message) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:10px;'>" +
               "<tr><td align='right'>" +
               "<table cellpadding='10' cellspacing='0' border='0' style='background-color:#F0FDF4; border:1px solid #BBF7D0; border-right:6px solid #059669; min-width:320px;'>" +
               "<tr><td align='right'>" +
               "<span style='background-color:#059669; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:2px 8px;'>CONFIDENTIAL MEMO SENT</span>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;•&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br/>" +
               "<div style='margin-top:6px;' align='left'><font face='Segoe UI' size='4' color='#064E3B'>" + escapeHtml(message) + "</font></div>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildDocumentBubble(String timestamp, String sender, String fileName, long size, String channelTag, boolean isOutgoing) {
        String docType = getDocumentTypeBadge(fileName);
        String sizeStr = formatFileSize(size);
        String cardBg = isOutgoing ? "#EFF6FF" : "#FFFFFF";
        String border = isOutgoing ? "#93C5FD" : "#CBD5E1";

        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin-bottom:12px;'>" +
               "<tr><td align='" + (isOutgoing ? "right" : "left") + "'>" +
               "<table cellpadding='12' cellspacing='0' border='0' style='background-color:" + cardBg + "; border:1px solid " + border + "; border-left:6px solid #2563EB; min-width:340px;'>" +
               "<tr><td>" +
               "<span style='background-color:#2563EB; color:#ffffff; font-family:Segoe UI; font-size:10px; font-weight:bold; padding:3px 9px;'>DOCUMENT DISPATCH</span>" +
               "<font face='Segoe UI' size='2' color='#94A3B8'>&nbsp;&nbsp;•&nbsp;&nbsp;" + escapeHtml(timestamp) + "</font><br/>" +
               "<div style='margin-top:6px;'><font face='Segoe UI' size='2' color='#475569'><b>" + escapeHtml(channelTag) + "</b></font></div>" +
               "<table cellpadding='8' cellspacing='0' border='0' style='background-color:#F8FAFC; border:1px solid #E2E8F0; margin-top:8px; width:100%;'>" +
               "<tr><td>" +
               "<font face='Segoe UI' size='2' color='#64748B'><b>" + escapeHtml(docType) + "</b></font><br/>" +
               "<font face='Segoe UI' size='3' color='#0F172A'><b>" + escapeHtml(fileName) + "</b></font><br/>" +
               "<div style='margin-top:4px;'><span style='background-color:#DCFCE7; color:#15803D; font-family:Segoe UI; font-size:11px; font-weight:bold; padding:2px 8px;'>Saved to downloads/</span> <font face='Segoe UI' size='2' color='#64748B'>(" + sizeStr + ")</font></div>" +
               "</td></tr></table>" +
               "</td></tr></table></td></tr></table>";
    }

    private String buildSystemNotice(String text) {
        return "<table width='100%' cellpadding='0' cellspacing='0' border='0' style='margin:10px 0;'>" +
               "<tr><td align='center'>" +
               "<span style='background-color:#F1F5F9; border:1px solid #CBD5E1; color:#475569; font-family:Segoe UI; font-size:11px; font-weight:600; padding:4px 14px;'>— " + escapeHtml(text) + " —</span>" +
               "</td></tr></table>";
    }

    private void sendMessage() {
        String text = messageInputField.getText().trim();
        if (text.isEmpty()) return;

        if (KEY_MAIN_BOARD.equals(selectedTarget)) {
            if (!isCurrentUserAuthority()) {
                JOptionPane.showMessageDialog(this, "Permission Denied: Only Principal and HODs can broadcast on the Main Notice Board.", "Read-Only Channel", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String cat = (String) noticeCategoryCombo.getSelectedItem();
            String prefix = getCategoryPrefix(cat);
            client.sendBroadcastMain(prefix + text);
        } else if (KEY_DEPT_BOARD.equals(selectedTarget)) {
            String cat = (String) noticeCategoryCombo.getSelectedItem();
            String prefix = getCategoryPrefix(cat);
            client.sendBroadcastDept(prefix + text);
        } else {
            client.sendPrivate(selectedTarget, text);
        }
        messageInputField.setText("");
    }

    private String getCategoryPrefix(String cat) {
        if (cat != null) {
            if (cat.contains("Urgent")) return "[URGENT] ";
            if (cat.contains("Meeting")) return "[MEETING] ";
            if (cat.contains("Exam")) return "[EXAM] ";
            if (cat.contains("Lab")) return "[LAB] ";
        }
        return "[CIRCULAR] ";
    }

    // =========================================================================
    // 6. CLIENT LISTENER CALLBACKS
    // =========================================================================
    @Override
    public void onLoginSuccess(String username, String department, String designation) {
        SwingUtilities.invokeLater(() -> {
            cardLayout.show(mainContainer, VIEW_CHAT);
            currentUserLabel.setText(username + "  •  " + designation + " (" + department + ")");
            setTitle("JECRC StaffNet — " + username + " [" + designation + " - " + department + "]");
            loginBtn.setEnabled(true);
            registerBtn.setEnabled(true);

            tabbedPane.removeAll();
            conversations.clear();

            // 1. Central Main Campus Notice Board
            getOrCreateConversation(KEY_MAIN_BOARD, "Main Notice Board", true);

            // 2. Department-specific Notice Board
            getOrCreateConversation(KEY_DEPT_BOARD, department + " Notice Board", false);

            updateChannelControls();
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
    public void onMainNoticeReceived(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_MAIN_BOARD, "Main Notice Board", false);
            boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());
            win.appendHtmlRow(buildNoticeBubble(timestamp, sender, message, isMe, "Main Notice Board"));
        });
    }

    @Override
    public void onDeptNoticeReceived(String timestamp, String sender, String department, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_DEPT_BOARD, department + " Notice Board", false);
            boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());
            win.appendHtmlRow(buildNoticeBubble(timestamp, sender, message, isMe, department + " Notice Board"));
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
            ConversationWindow win = getOrCreateConversation(KEY_MAIN_BOARD, "Main Notice Board", false);
            win.appendHtmlRow(buildSystemNotice(message));
        });
    }

    @Override
    public void onUserListUpdated(List<String> rawUsers) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            staffDirectory.clear();

            // 1. Main Notice Board
            userListModel.addElement(new DirectoryEntry(KEY_MAIN_BOARD, "Main Campus Notice Board", "All Departments", "Principal & HOD Directives", 0));

            // 2. Department Notice Board
            String myDept = client.getCurrentDepartment();
            userListModel.addElement(new DirectoryEntry(KEY_DEPT_BOARD, myDept + " Notice Board", myDept, "Department Faculty", 1));

            int count = 0;
            for (String raw : rawUsers) {
                if (raw == null || raw.trim().isEmpty()) continue;
                String[] parts = raw.split(":");
                String u = parts[0].trim();
                String dep = (parts.length >= 2) ? parts[1].trim() : "General";
                String des = (parts.length >= 3) ? parts[2].trim() : "Faculty";

                DirectoryEntry staff = new DirectoryEntry(u, u, dep, des, 2);
                staffDirectory.put(u.toLowerCase(), staff);
                userListModel.addElement(staff);
                count++;
            }
            contactsCountLabel.setText(count + " staff active");
        });
    }

    @Override
    public void onHistoryMainNotice(String timestamp, String sender, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_MAIN_BOARD, "Main Notice Board", false);
            boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());
            win.appendHtmlRow(buildNoticeBubble(timestamp, sender, message, isMe, "Main Notice Board"));
        });
    }

    @Override
    public void onHistoryDeptNotice(String timestamp, String sender, String department, String message) {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_DEPT_BOARD, department + " Notice Board", false);
            boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());
            win.appendHtmlRow(buildNoticeBubble(timestamp, sender, message, isMe, department + " Notice Board"));
        });
    }

    @Override
    public void onHistoryPrivate(String timestamp, String sender, String receiver, String message) {
        SwingUtilities.invokeLater(() -> {
            String partner = sender.equalsIgnoreCase(client.getCurrentUsername()) ? receiver : sender;
            ConversationWindow win = getOrCreateConversation(partner, partner, false);
            if (sender.equalsIgnoreCase(client.getCurrentUsername())) {
                win.appendHtmlRow(buildOutgoingPrivateBubble(timestamp, message));
            } else {
                win.appendHtmlRow(buildIncomingPrivateBubble(timestamp, sender, message));
            }
        });
    }

    @Override
    public void onHistoryDone() {
        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_MAIN_BOARD, "Main Notice Board", false);
            win.appendHtmlRow(buildSystemNotice("Staff history synchronized with MySQL database"));
        });
    }

    @Override
    public void onFileMainReceived(String timestamp, String sender, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;
        boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_MAIN_BOARD, "Main Notice Board", false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, sender, fileName, size, "Main Campus Notice Board: " + sender, isMe));
        });
    }

    @Override
    public void onFileDeptReceived(String timestamp, String sender, String department, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;
        boolean isMe = sender.equalsIgnoreCase(client.getCurrentUsername());

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(KEY_DEPT_BOARD, department + " Notice Board", false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, sender, fileName, size, department + " Notice Board: " + sender, isMe));
        });
    }

    @Override
    public void onFilePrivateReceived(String timestamp, String sender, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(sender, sender, false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, sender, fileName, size, "Confidential Memo: " + sender, false));
        });
    }

    @Override
    public void onFilePrivateSent(String timestamp, String recipient, String fileName, byte[] fileData) {
        File saved = saveReceivedFile(fileName, fileData);
        long size = (saved != null) ? saved.length() : fileData.length;

        SwingUtilities.invokeLater(() -> {
            ConversationWindow win = getOrCreateConversation(recipient, recipient, false);
            win.appendHtmlRow(buildDocumentBubble(timestamp, recipient, fileName, size, "Confidential Memo to: " + recipient, true));
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
                g2.setColor(getModel().isRollover() && isEnabled() ? hoverBg : (isEnabled() ? bg : new Color(203, 213, 225)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(fg);
        btn.setPreferredSize(new Dimension(width, height));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private class DirectoryCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 2));
            p.setBorder(new EmptyBorder(8, 12, 8, 12));

            if (isSelected) {
                p.setBackground(new Color(238, 242, 255));
            } else {
                p.setBackground(Color.WHITE);
            }

            if (value instanceof DirectoryEntry) {
                DirectoryEntry entry = (DirectoryEntry) value;
                if (entry.type == 0) {
                    // Main Board
                    JLabel title = new JLabel("Main Campus Notice Board");
                    title.setIcon(AppIcons.MAIN_BOARD_16);
                    title.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    title.setForeground(COLOR_PRIMARY);

                    JLabel sub = new JLabel("Principal & HODs • All Departments");
                    sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    sub.setForeground(new Color(100, 116, 139));

                    JPanel col = new JPanel();
                    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
                    col.setOpaque(false);
                    col.add(title);
                    col.add(sub);
                    p.add(col, BorderLayout.CENTER);
                } else if (entry.type == 1) {
                    // Department Board
                    JLabel title = new JLabel(entry.department + " Notice Board");
                    title.setIcon(AppIcons.DEPT_BOARD_16);
                    title.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    title.setForeground(new Color(5, 150, 105));

                    JLabel sub = new JLabel("Department Faculty Only");
                    sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    sub.setForeground(new Color(100, 116, 139));

                    JPanel col = new JPanel();
                    col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
                    col.setOpaque(false);
                    col.add(title);
                    col.add(sub);
                    p.add(col, BorderLayout.CENTER);
                } else {
                    // Staff Member
                    String me = client.getCurrentUsername();
                    boolean isMe = (me != null && entry.key.equalsIgnoreCase(me));
                    String label = entry.key + (isMe ? " (You)" : "");

                    JLabel name = new JLabel(label);
                    name.setIcon(AppIcons.DOT_ONLINE);
                    name.setFont(new Font("Segoe UI", Font.BOLD, 13));
                    name.setForeground(isMe ? COLOR_PRIMARY : COLOR_TEXT_MAIN);

                    JLabel meta = new JLabel(entry.designation + " • " + entry.department);
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
