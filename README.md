# Client-Server Chat Application (Advanced Java Lab)

A complete, production-grade Client-Server Chat Application developed in **Core Java** (JDK 25) with **MySQL 8.0**, **Java Sockets (`java.net`)**, **Multithreading**, and **Swing GUI**.

---

## 1. Project Overview & Features

- **Authentication**: User registration and login verified against a MySQL `users` database table with secure **SHA-256 password hashing**.
- **Multithreaded Server**: Uses `ExecutorService` (cached thread pool) and `ServerSocket` to handle concurrent client connections.
- **Thread-safe Session Management**: Tracks online clients using `ConcurrentHashMap<String, ClientHandler>`.
- **Group / Broadcast Chat**: Broadcast messages sent to all connected users and stored in the database (`receiver = 'ALL'`).
- **Private 1-to-1 Chat**: Direct messaging between selected active users (`receiver = username`) with sender confirmations and offline persistence.
- **Real-Time Active User List**: Dynamic `JList` showing online contacts with status indicators (`🟢`). Selecting a contact switches between Broadcast and Private chat modes.
- **Timestamped Chat Log**: Formatted message log using `JTextPane` with custom text styling for timestamps, senders, private notes, and system notifications.
- **Persistent Chat History**: Previous conversations automatically retrieved from MySQL upon user login.
- **Clean Disconnect Handling**: Handles window closing events, socket termination, and session teardown cleanly.

---

## 2. Directory Structure

```
ChatApp/
├── server/
│   ├── ChatServer.java       # Central TCP ServerSocket & thread pool manager
│   ├── ClientHandler.java    # Runnable worker handling socket I/O & protocol
│   └── DBHandler.java        # JDBC connectivity, queries, & SHA-256 hashing
├── client/
│   ├── ChatClient.java       # TCP Client socket manager & background listener
│   └── ChatGUI.java          # Swing GUI (Login view & Main chat window)
├── lib/
│   └── mysql-connector-j-9.7.0.jar # Official MySQL JDBC Driver
├── database/
│   └── schema.sql            # MySQL table DDL and initial test data
├── db.properties             # Configurable database credentials
├── compile.bat               # 1-click batch compilation script
├── run-server.bat            # 1-click batch script to launch the server
├── run-client.bat            # 1-click batch script to launch a client
└── README.md
```

---

## 3. Database Setup (MySQL)

### Option A: Using MySQL Command Line Client
1. Open MySQL CLI or PowerShell:
   ```powershell
   & "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p < database\schema.sql
   ```
2. Enter your MySQL root password when prompted.

### Option B: Using MySQL Workbench
1. Open **MySQL Workbench** and connect to your local MySQL instance.
2. Go to **File -> Open SQL Script...** and select `ChatApp/database/schema.sql`.
3. Click the **Execute (Lightning Bolt)** icon to execute the script.

### Configure Database Credentials
Open `ChatApp/db.properties` and ensure `db.user` and `db.password` match your local MySQL installation:
```properties
db.url=jdbc:mysql://localhost:3306/chatapp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=YOUR_MYSQL_PASSWORD
```

---

## 4. How to Compile and Run

### Step 1: Compile the Project
Double-click `compile.bat` or run from terminal inside `ChatApp/`:
```powershell
javac -cp "lib/mysql-connector-j-9.7.0.jar;." server/*.java client/*.java
```

### Step 2: Start the Server
Double-click `run-server.bat` or run from terminal:
```powershell
java -cp "lib/mysql-connector-j-9.7.0.jar;." server.ChatServer
```
You should see:
```
[Server] Testing database connection via JDBC...
[Server] Database connection OK!
[Server] Listening for client connections on TCP port: 12345
[Server] Ready to accept incoming clients...
```

### Step 3: Start Clients for Testing
You can launch as many client instances as you want:
- Double-click `run-client.bat` twice to open two independent client windows.
- Or run via terminal:
  ```powershell
  java -cp "lib/mysql-connector-j-9.7.0.jar;." client.ChatGUI
  ```

---

## 5. How to Test and Demo the Application

1. **User Registration**:
   - On Client 1, enter username `Alice` and password `password123`. Click **Register New User**.
   - On Client 2, enter username `Bob` and password `password123`. Click **Register New User**.
2. **User Login**:
   - Log in as `Alice` on Client 1.
   - Log in as `Bob` on Client 2.
   - Observe both clients update their **Active Contacts** list in real-time (`🟢 Alice (You)`, `🟢 Bob`).
3. **Group Broadcast Chat**:
   - Ensure `🌐 Everyone (Group Chat)` is selected.
   - Type `Hello everyone!` from Alice and press Enter. Both Alice and Bob see the message with timestamp `[HH:mm:ss] [GROUP] Alice: Hello everyone!`.
4. **Private 1-to-1 Messaging**:
   - On Alice's client, click on `🟢 Bob` in the contacts list.
   - Notice the status bar changes to `Sending to: 🔒 Bob (Private Message)`.
   - Send `Hey Bob, this is a secret message.`.
   - Alice sees `[PRIVATE to Bob]: ...` and Bob sees `[PRIVATE from Alice]: ...`.
5. **Chat History Persistence**:
   - Close Bob's client.
   - Alice sees `*** User 'Bob' has left the chat. ***` and the contacts list updates.
   - Re-open Bob's client and log in again. Notice historical group and private messages are automatically reloaded!

---

## 6. Viva Voce & Demonstration Guide

### Key Concepts Demonstrated:
1. **Socket Programming (`java.net`)**:
   - `ServerSocket` creates a TCP listener on a designated port.
   - `Socket` establishes a full-duplex, reliable byte stream over TCP/IP.
   - Custom pipe-delimited application-layer protocol (`COMMAND|PARAM1|PARAM2`).
2. **Multithreading & Concurrency (`java.util.concurrent`)**:
   - `ExecutorService` (via `Executors.newCachedThreadPool()`) prevents the overhead of manual thread creation and manages worker threads dynamically.
   - `ClientHandler` implements `Runnable` to handle each client in isolation without blocking others.
   - `ConcurrentHashMap` avoids race conditions and synchronization bottlenecks when users log in, log out, or send messages concurrently.
3. **Swing GUI & Event Dispatch Thread (EDT)**:
   - Swing components are not thread-safe.
   - Socket I/O runs on a dedicated background thread to prevent GUI freezing.
   - Inbound socket events update UI components strictly via `SwingUtilities.invokeLater()`.
4. **JDBC & Database Architecture (`java.sql`)**:
   - Driver registration using `Class.forName("com.mysql.cj.jdbc.Driver")`.
   - Use of `PreparedStatement` to pre-compile SQL queries and prevent SQL Injection attacks.
   - Use of `ResultSet` navigation for query processing.
5. **Security**:
   - Passwords are never stored in plain text. They are hashed using standard `SHA-256` (`java.security.MessageDigest`).
