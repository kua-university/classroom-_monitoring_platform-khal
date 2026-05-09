package classroomserver;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * MAIN CLASS - Teacher Dashboard
 * Controls the entire classroom from teacher's side
 */
public class ServerMain extends Application {

    // ============================================================
    // SERVER CONFIGURATION
    // ============================================================
    private static final int PORT = 9010; // Port for student connections
    private ServerSocket serverSocket; // Server socket listener
    private boolean isRunning = true; // Server status flag

    // ============================================================
    // UI COMPONENTS
    // ============================================================
    private TextArea logArea; // Activity log display
    private ListView<String> studentListView; // List of connected students
    private Label connectedCountLabel; // Shows number of online students
    private Label serverStatusIndicator; // Shows server status dot

    // ============================================================
    // DATA STORAGE
    // ============================================================
    private final Map<String, StudentSession> activeSessions = new ConcurrentHashMap<>();
    private final List<Map<String, String>> quizQueue = new ArrayList<>();
    private final Map<Integer, Integer> studentProgress = new ConcurrentHashMap<>();
    private final int currentQuestionIndex = 0;

    // ============================================================
    // CSS STYLES (for consistent look)
    // ============================================================
    private static final String BUTTON_BLUE = "-fx-background-color: linear-gradient(to right, #3498db, #2980b9); " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; " +
            "-fx-padding: 8 20 8 20; -fx-cursor: hand;";

    private static final String BUTTON_GREEN = "-fx-background-color: linear-gradient(to right, #27ae60, #229954); " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; " +
            "-fx-padding: 8 20 8 20; -fx-cursor: hand;";

    private static final String BUTTON_RED = "-fx-background-color: linear-gradient(to right, #e74c3c, #c0392b); " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; " +
            "-fx-padding: 8 20 8 20; -fx-cursor: hand;";

    // ============================================================
    // STUDENT SESSION MODEL
    // Stores information about each connected student
    // ============================================================
    public static class StudentSession {
        public int studentId;
        public String username;
        public String fullName;
        public String sessionToken;
        public long lastHeartbeat;
        public Socket socket;
        public PrintWriter out;
        public BufferedReader in;

        public StudentSession(int id, String user, String name, String token) {
            this.studentId = id;
            this.username = user;
            this.fullName = name;
            this.sessionToken = token;
            this.lastHeartbeat = System.currentTimeMillis();
        }

        // Check if student is still active (heartbeat within 15 seconds)
        public boolean isActive() {
            return (System.currentTimeMillis() - lastHeartbeat) < 15000;
        }
    }

    // ============================================================
    // MAIN UI - ENTRY POINT
    // ============================================================
    @Override
    public void start(Stage stage) {
        // ------------------------------------------------------------
        // FIX: Auto‑create tables on first run
        // ------------------------------------------------------------
        DatabaseConnection.setupDatabase();
        // Main layout container

        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #f0f4f8, #e2e8f0);");

        // Create the three main sections
        VBox headerBox = createHeader(); // Top bar with title and buttons
        HBox statsBar = createStatsBar(); // Statistics cards
        HBox contentBox = createContentArea(); // Left, Center, Right panels

        // Assemble the layout
        mainLayout.setTop(headerBox);
        mainLayout.setCenter(contentBox);

        // Create and show the scene
        Scene scene = new Scene(mainLayout, 1500, 850);
        stage.setScene(scene);
        stage.setTitle("🎓 Classroom Monitor - Teacher Dashboard");
        stage.show();

        // Animate the server status dot
        animateStatusDot();

        // Update student list every 2 seconds
        Timeline updater = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            updateStudentList();
            updateConnectedCount();
        }));
        updater.setCycleCount(Animation.INDEFINITE);
        updater.play();
    }

    // ============================================================
    // HEADER SECTION (Top bar)
    // ============================================================
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(20, 30, 20, 30));
        header.setStyle("-fx-background-color: linear-gradient(to right, #1a1a2e, #16213e);");

        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setSpacing(20);

        // Logo section
        StackPane logoPane = new StackPane();
        logoPane.setStyle("-fx-background-color: #e94560; -fx-background-radius: 15;");
        logoPane.setPadding(new Insets(10, 15, 10, 15));
        Label logoLabel = new Label("🎓");
        logoLabel.setFont(Font.font(28));
        logoPane.getChildren().add(logoLabel);

        // Title section
        VBox titleBox = new VBox(5);
        Label titleLabel = new Label("Classroom Monitor");
        titleLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);

        Label subtitleLabel = new Label("Real-time Classroom Management System");
        subtitleLabel.setFont(Font.font("Poppins", 12));
        subtitleLabel.setTextFill(Color.web("#a8b2d1"));
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        // Spacer to push controls to right
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Server control panel
        VBox controlBox = new VBox(8);
        controlBox.setStyle("-fx-background-color: rgba(255,255,255,0.1);");
        controlBox.setStyle(controlBox.getStyle() + "-fx-background-radius: 15; -fx-padding: 12 20;");

        // Status indicator
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER);
        serverStatusIndicator = new Label("●");
        serverStatusIndicator.setFont(Font.font(16));
        serverStatusIndicator.setTextFill(Color.RED);

        Label statusText = new Label("Server Stopped");
        statusText.setFont(Font.font("Poppins", FontWeight.MEDIUM, 12));
        statusText.setTextFill(Color.RED);
        statusBox.getChildren().addAll(serverStatusIndicator, statusText);

        // Buttons
        HBox buttonBox = new HBox(10);
        Button registerBtn = createStyledButton("📝 Register Student", "#9b59b6");
        registerBtn.setOnAction(e -> showRegisterDialog());

        Button startBtn = createStyledButton("▶ Start Server", "#27ae60");
        startBtn.setOnAction(e -> {
            startServer();
            statusText.setText("Server Running");
            statusText.setTextFill(Color.web("#2ecc71"));
            startBtn.setDisable(true);
        });
        buttonBox.getChildren().addAll(registerBtn, startBtn);

        controlBox.getChildren().addAll(statusBox, buttonBox);

        // Assemble top row
        topRow.getChildren().addAll(logoPane, titleBox, spacer, controlBox);
        header.getChildren().add(topRow);
        return header;
    }

    // Helper to create styled buttons
    private Button createStyledButton(String text, String color) {
        Button btn = new Button(text);
        String style = String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 25; -fx-padding: 8 20; -fx-cursor: hand; -fx-font-size: 12px;",
                color);
        btn.setStyle(style);

        btn.setOnMouseEntered(e -> btn.setStyle(
                String.format("-fx-background-color: derive(%s, 20%%); -fx-text-fill: white; " +
                        "-fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 8 20; -fx-cursor: hand;",
                        color)));
        btn.setOnMouseExited(e -> btn.setStyle(style));
        return btn;
    }

    // ============================================================
    // STATISTICS BAR
    // ============================================================
    private HBox createStatsBar() {
        HBox bar = new HBox(20);
        bar.setPadding(new Insets(15, 30, 15, 30));
        bar.setStyle("-fx-background-color: white;");

        // Create four statistic cards
        VBox stat1 = createStatCard("👥", "Registered", getRegisteredCount(), "#3498db");
        VBox stat2 = createStatCard("🟢", "Online", "0", "#27ae60");
        VBox stat3 = createStatCard("📝", "Quizzes", getQuizCount(), "#9b59b6");
        VBox stat4 = createStatCard("✅", "Responses", getResponseCount(), "#e74c3c");

        connectedCountLabel = (Label) stat2.getChildren().get(1);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(stat1, stat2, stat3, stat4, spacer);
        return bar;
    }

    // Create a single statistics card
    private VBox createStatCard(String icon, String title, String value, String color) {
        VBox card = new VBox(5);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(12, 25, 12, 25));
        card.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 12;");

        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font(28));

        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 22));
        valueLabel.setTextFill(Color.web(color));

        Label titleLabel = new Label(title);
        titleLabel.setFont(Font.font("Poppins", 11));
        titleLabel.setTextFill(Color.web("#7f8c8d"));

        card.getChildren().addAll(iconLabel, valueLabel, titleLabel);
        return card;
    }

    // ============================================================
    // MAIN CONTENT AREA (3 panels)
    // ============================================================
    private HBox createContentArea() {
        HBox content = new HBox(20);
        content.setPadding(new Insets(20));

        VBox leftPanel = createStudentListPanel(); // Connected students
        VBox centerPanel = createActionsPanel(); // Action buttons
        VBox rightPanel = createLogPanel(); // Activity log

        content.getChildren().addAll(leftPanel, centerPanel, rightPanel);
        HBox.setHgrow(centerPanel, Priority.ALWAYS);
        return content;
    }

    // LEFT PANEL - List of connected students
    private VBox createStudentListPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 15;");
        panel.setPrefWidth(320);

        // Header with count badge
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("👨‍🎓 Online Students");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label badge = new Label("0");
        badge.setStyle("-fx-background-color: #e94560; -fx-background-radius: 20; " +
                "-fx-text-fill: white; -fx-padding: 2 8; -fx-font-size: 11px;");
        connectedCountLabel = badge;
        header.getChildren().addAll(title, spacer, badge);

        // Student list view
        studentListView = new ListView<>();
        studentListView.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-radius: 10;");
        studentListView.setPrefHeight(450);

        // Color-code list items based on status
        studentListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    setStyle("-fx-background-color: #f8f9fa; -fx-padding: 12;");
                    if (item.contains("🟢")) {
                        setStyle("-fx-text-fill: #27ae60; -fx-background-color: #f0fdf4; " +
                                "-fx-padding: 12; -fx-font-weight: bold; -fx-border-color: #d1fae5; " +
                                "-fx-border-width: 0 0 0 3;");
                    } else if (item.contains("🔴")) {
                        setStyle("-fx-text-fill: #95a5a6; -fx-background-color: #f8f9fa; -fx-padding: 12;");
                    }
                }
            }
        });

        panel.getChildren().addAll(header, studentListView);
        return panel;
    }

    // CENTER PANEL - All action buttons
    private VBox createActionsPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 15;");
        panel.setPrefWidth(400);

        Label title = new Label("⚡ Quick Actions");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 16));

        // Create collapsible sections
        TitledPane announcePane = createTitledPane("📢 Send Announcement", createAnnouncementContent());
        TitledPane quizPane = createTitledPane("📝 Create Quiz", createQuizContent());
        TitledPane managePane = createTitledPane("👥 Manage Students", createManageContent());
        TitledPane attendancePane = createTitledPane("📋 Attendance", createAttendanceContent());
        TitledPane controlPane = createTitledPane("🎮 Classroom Controls", createControlContent());

        panel.getChildren().addAll(title, announcePane, quizPane, managePane, attendancePane, controlPane);
        return panel;
    }

    // Helper for collapsible sections
    private TitledPane createTitledPane(String title, VBox content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-radius: 10;");
        pane.setFont(Font.font("Poppins", FontWeight.BOLD, 13));
        return pane;
    }

    // Announcement section content
    private VBox createAnnouncementContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        TextField announcementField = new TextField();
        announcementField.setPromptText("✏️ Type your announcement here...");
        announcementField.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; " +
                "-fx-border-radius: 10; -fx-padding: 12;");

        Button sendBtn = createActionButton("📢 Send to All Students", "#3498db");
        sendBtn.setMaxWidth(Double.MAX_VALUE);

        sendBtn.setOnAction(e -> {
            String msg = announcementField.getText();
            if (!msg.isEmpty()) {
                broadcastToAll("ANNOUNCEMENT:" + msg);
                log("📢 Announcement: " + msg);
                announcementField.clear();
            }
        });

        content.getChildren().addAll(announcementField, sendBtn);
        return content;
    }

    // Quiz section content
    private VBox createQuizContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        Button createBtn = createActionButton("➕ Create Multi-Question Quiz", "#9b59b6");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> showQuizDialog());

        content.getChildren().addAll(createBtn);
        return content;
    }

    // Manage students section
    private VBox createManageContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        Button registerBtn = createActionButton("📝 Register New Student", "#27ae60");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        registerBtn.setOnAction(e -> showRegisterDialog());

        Button viewBtn = createActionButton("👥 View All Students", "#3498db");
        viewBtn.setMaxWidth(Double.MAX_VALUE);
        viewBtn.setOnAction(e -> showStudentsDialog());

        content.getChildren().addAll(registerBtn, viewBtn);
        return content;
    }

    // Attendance section
    private VBox createAttendanceContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        Button reportBtn = createActionButton("📋 View Attendance Report", "#e67e22");
        reportBtn.setMaxWidth(Double.MAX_VALUE);
        reportBtn.setOnAction(e -> showAttendanceReport());

        Button markBtn = createActionButton("✅ Mark All Present", "#27ae60");
        markBtn.setMaxWidth(Double.MAX_VALUE);
        markBtn.setOnAction(e -> markAllPresent());

        content.getChildren().addAll(reportBtn, markBtn);
        return content;
    }

    // Classroom controls section (with LOCK buttons)
    private VBox createControlContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(15));

        HBox lockBox = new HBox(10);
        lockBox.setMaxWidth(Double.MAX_VALUE);

        Button lockBtn = createActionButton("🔒 Lock Screens", "#e74c3c");
        lockBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lockBtn, Priority.ALWAYS);

        Button unlockBtn = createActionButton("🔓 Unlock Screens", "#27ae60");
        unlockBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(unlockBtn, Priority.ALWAYS);

        Button resetBtn = createActionButton("🔄 Reset Lock", "#f39c12");
        resetBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(resetBtn, Priority.ALWAYS);

        lockBox.getChildren().addAll(lockBtn, unlockBtn, resetBtn);

        lockBtn.setOnAction(e -> {
            broadcastToAll("LOCK_SCREEN");
            log("🔒 Screen lock command sent");
        });

        unlockBtn.setOnAction(e -> {
            broadcastToAll("UNLOCK_SCREEN");
            log("🔓 Screen unlock command sent");
        });

        resetBtn.setOnAction(e -> {
            broadcastToAll("UNLOCK_SCREEN");
            log("🔄 Screen lock reset command sent");
        });

        Button reportBtn = createActionButton("📊 Generate Detailed Report", "#f39c12");
        reportBtn.setMaxWidth(Double.MAX_VALUE);
        reportBtn.setOnAction(e -> generateReport());

        content.getChildren().addAll(lockBox, reportBtn);
        return content;
    }

    // Helper for action buttons
    private Button createActionButton(String text, String color) {
        Button btn = new Button(text);
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 10; -fx-padding: 10 15; -fx-cursor: hand; -fx-font-size: 13px;",
                color));

        btn.setOnMouseEntered(e -> btn.setStyle(String.format(
                "-fx-background-color: derive(%s, 15%%); -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 10; -fx-padding: 10 15; -fx-cursor: hand;",
                color)));
        btn.setOnMouseExited(e -> btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: white; -fx-font-weight: bold; " +
                        "-fx-background-radius: 10; -fx-padding: 10 15; -fx-cursor: hand;",
                color)));
        return btn;
    }

    // RIGHT PANEL - Activity log
    private VBox createLogPanel() {
        VBox panel = new VBox(12);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 15;");
        panel.setPrefWidth(420);

        // Log header with clear button
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("📋 Activity Log");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 15));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: #ecf0f1; -fx-text-fill: #7f8c8d; " +
                "-fx-background-radius: 20; -fx-padding: 4 15; -fx-cursor: hand;");
        clearBtn.setOnAction(e -> logArea.clear());

        header.getChildren().addAll(title, spacer, clearBtn);

        // Log text area
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.setStyle("-fx-background-color: #1a1a2e; -fx-text-fill: #a8b2d1; " +
                "-fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-border-radius: 10;");
        logArea.setPrefHeight(430);

        panel.getChildren().addAll(header, logArea);
        return panel;
    }

    // ============================================================
    // QUIZ DIALOG
    // ============================================================
    private void showQuizDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Create Multi-Question Quiz");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(15);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 15;");

        // Title
        Label title = new Label("📝 Create Multi-Question Quiz");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 20));

        // Question counter
        Label counter = new Label("Question #" + (quizQueue.size() + 1));
        counter.setStyle("-fx-font-weight: bold; -fx-text-fill: #9b59b6;");

        // Question input
        Label qLabel = new Label("Question:");
        TextArea qField = new TextArea();
        qField.setPrefHeight(80);
        qField.setPromptText("Enter your question here...");

        // Options grid
        GridPane optionsGrid = new GridPane();
        optionsGrid.setHgap(15);
        optionsGrid.setVgap(12);

        TextField[] options = new TextField[4];
        for (int i = 0; i < 4; i++) {
            Label optLabel = new Label((char) ('A' + i) + ")");
            optLabel.setStyle("-fx-font-weight: bold;");
            options[i] = new TextField();
            options[i].setPromptText("Option " + (char) ('A' + i));
            optionsGrid.add(optLabel, 0, i);
            optionsGrid.add(options[i], 1, i);
        }

        // Correct answer selector
        Label correctLabel = new Label("Correct Answer:");
        ComboBox<String> correctAnswer = new ComboBox<>();
        correctAnswer.getItems().addAll("A", "B", "C", "D");

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button addBtn = new Button("➕ Add to Quiz");
        addBtn.setStyle(BUTTON_BLUE);

        Button sendBtn = new Button("📤 Send All Questions");
        sendBtn.setStyle(BUTTON_GREEN);
        sendBtn.setDisable(true);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle(BUTTON_RED);

        buttonBox.getChildren().addAll(addBtn, sendBtn, cancelBtn);

        // Queue display
        Label queueLabel = new Label("📋 Quiz Queue:");
        ListView<String> queueList = new ListView<>();
        queueList.setPrefHeight(120);

        // Add question to queue
        addBtn.setOnAction(e -> {
            String question = qField.getText();
            String optA = options[0].getText();
            String optB = options[1].getText();
            String optC = options[2].getText();
            String optD = options[3].getText();
            String correct = correctAnswer.getValue();

            if (question.isEmpty() || optA.isEmpty() || optB.isEmpty() || correct == null) {
                showAlert("Incomplete Question", "Please fill all required fields!");
                return;
            }

            // Store question
            Map<String, String> quizQuestion = new HashMap<>();
            quizQuestion.put("question", question);
            quizQuestion.put("optA", optA);
            quizQuestion.put("optB", optB);
            quizQuestion.put("optC", optC);
            quizQuestion.put("optD", optD);
            quizQuestion.put("correct", correct);
            quizQueue.add(quizQuestion);

            // Update display
            String preview = question.length() > 50 ? question.substring(0, 50) + "..." : question;
            queueList.getItems().add("Q" + quizQueue.size() + ": " + preview);

            // Clear fields
            qField.clear();
            for (TextField opt : options)
                opt.clear();
            correctAnswer.setValue(null);

            counter.setText("Question #" + (quizQueue.size() + 1));
            sendBtn.setDisable(false);

            log("📝 Question added. Total: " + quizQueue.size());
        });

        // Send all questions
        sendBtn.setOnAction(e -> {
            if (quizQueue.isEmpty()) {
                showAlert("No Questions", "Please add at least one question!");
                return;
            }

            // Send each question with delay
            new Thread(() -> {
                for (int i = 0; i < quizQueue.size(); i++) {
                    Map<String, String> q = quizQueue.get(i);
                    int qNum = i + 1;

                    String msg = String.format(
                            "QUIZ:%d/%d|%s|A:%s|B:%s|C:%s|D:%s|CORRECT:%s",
                            qNum, quizQueue.size(),
                            q.get("question"),
                            q.get("optA"), q.get("optB"),
                            q.get("optC"), q.get("optD"),
                            q.get("correct"));

                    broadcastToAll(msg);
                    log("📝 Sent Question " + qNum + "/" + quizQueue.size());

                    try {
                        Thread.sleep(500);
                    } catch (Exception ex) {
                    }
                }
                log("📝 All " + quizQueue.size() + " questions sent!");
            }).start();

            dialog.close();
            showAlert("Quiz Started", quizQueue.size() + " questions sent to students!");
        });

        cancelBtn.setOnAction(e -> {
            quizQueue.clear();
            dialog.close();
        });

        root.getChildren().addAll(title, counter, qLabel, qField, optionsGrid,
                correctLabel, correctAnswer, buttonBox, queueLabel, queueList);

        Scene scene = new Scene(root, 680, 700);
        dialog.setScene(scene);
        dialog.show();
    }

    // ============================================================
    // REGISTRATION DIALOG
    // ============================================================
    private void showRegisterDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Register New Student");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 15;");

        Label title = new Label("📝 Register New Student");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 20));

        // Form grid
        GridPane grid = new GridPane();
        grid.setHgap(15);
        grid.setVgap(15);
        grid.setAlignment(Pos.CENTER);

        String[] labels = { "Username:", "Password:", "Full Name:", "Email:", "Class:" };
        TextField[] fields = new TextField[5];
        fields[1] = new PasswordField();

        for (int i = 0; i < labels.length; i++) {
            Label label = new Label(labels[i]);
            label.setStyle("-fx-font-weight: bold;");

            if (fields[i] == null)
                fields[i] = new TextField();
            fields[i].setPromptText(labels[i]);
            fields[i].setPrefWidth(250);

            grid.add(label, 0, i);
            grid.add(fields[i], 1, i);
        }

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-font-size: 12px;");

        Button registerBtn = new Button("✅ Register Student");
        registerBtn.setStyle(BUTTON_GREEN);
        registerBtn.setMaxWidth(Double.MAX_VALUE);

        registerBtn.setOnAction(e -> {
            String username = fields[0].getText().trim();
            String password = fields[1].getText();
            String fullName = fields[2].getText().trim();
            String email = fields[3].getText().trim();
            String className = fields[4].getText().trim();

            if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
                statusLabel.setText("❌ Please fill all required fields!");
                statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                return;
            }

            registerBtn.setDisable(true);
            registerBtn.setText("Registering...");

            new Thread(() -> {
                try (Connection conn = DatabaseConnection.getConnection();
                        PreparedStatement pstmt = conn.prepareStatement(
                                "INSERT INTO students (username, password, full_name, email, class_name, role) " +
                                        "VALUES (?, ?, ?, ?, ?, 'student')")) {

                    pstmt.setString(1, username);
                    pstmt.setString(2, hashPassword(password));
                    pstmt.setString(3, fullName);
                    pstmt.setString(4, email);
                    pstmt.setString(5, className);
                    pstmt.executeUpdate();

                    Platform.runLater(() -> {
                        statusLabel.setText("✅ Student registered successfully!");
                        statusLabel.setStyle("-fx-text-fill: #27ae60;");
                        for (TextField field : fields)
                            field.clear();

                        new Thread(() -> {
                            try {
                                Thread.sleep(2000);
                            } catch (Exception ex) {
                            }
                            Platform.runLater(dialog::close);
                        }).start();
                    });

                    log("📝 New student registered: " + username + " (" + fullName + ")");

                } catch (SQLException ex) {
                    Platform.runLater(() -> {
                        registerBtn.setDisable(false);
                        registerBtn.setText("✅ Register Student");
                        if (ex.getMessage().contains("unique constraint")) {
                            statusLabel.setText("❌ Username already exists!");
                        } else {
                            statusLabel.setText("❌ Error: " + ex.getMessage());
                        }
                        statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    });
                }
            }).start();
        });

        root.getChildren().addAll(title, grid, registerBtn, statusLabel);

        Scene scene = new Scene(root, 550, 500);
        dialog.setScene(scene);
        dialog.show();
    }

    // Display all registered students
    private void showStudentsDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Registered Students");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 15;");

        Label title = new Label("👥 Registered Students");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 18));

        ListView<String> list = new ListView<>();
        list.setPrefHeight(400);

        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT student_id, username, full_name, class_name, 0 AS is_active FROM students " +
                                    "WHERE role = 'student' ORDER BY full_name")) {

                while (rs.next()) {
                    String status = rs.getBoolean("is_active") ? "🟢 Online" : "⚫ Offline";
                    String info = String.format("%d | %s | %s | %s | %s",
                            rs.getInt("student_id"),
                            rs.getString("username"),
                            rs.getString("full_name"),
                            rs.getString("class_name") != null ? rs.getString("class_name") : "No class",
                            status);
                    Platform.runLater(() -> list.getItems().add(info));
                }
            } catch (SQLException e) {
                Platform.runLater(() -> list.getItems().add("Error: " + e.getMessage()));
            }
        }).start();

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(BUTTON_BLUE);
        closeBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, list, closeBtn);

        Scene scene = new Scene(root, 700, 500);
        dialog.setScene(scene);
        dialog.show();
    }

    // Show attendance report
    private void showAttendanceReport() {
        Stage dialog = new Stage();
        dialog.setTitle("Attendance Report");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        VBox root = new VBox(15);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 15;");

        Label title = new Label("📋 Attendance Report");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 18));

        TextArea reportArea = new TextArea();
        reportArea.setEditable(false);
        reportArea.setPrefHeight(400);
        reportArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        new Thread(() -> {
            StringBuilder report = new StringBuilder();
            report.append("═══════════════════════════════════════════════════\n");
            report.append("           ATTENDANCE REPORT\n");
            report.append("═══════════════════════════════════════════════════\n\n");
            report.append("Date: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .append("\n\n");

            try (Connection conn = DatabaseConnection.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT s.full_name, COUNT(CASE WHEN al.action = 'LOGIN' THEN 1 END) as present_count " +
                                    "FROM students s LEFT JOIN attendance_log al ON s.student_id = al.student_id " +
                                    "WHERE s.role = 'student' GROUP BY s.student_id ORDER BY s.full_name")) {

                int total = 0;
                while (rs.next()) {
                    total++;
                    String name = rs.getString("full_name");
                    int present = rs.getInt("present_count");
                    report.append(String.format("%-25s - Present: %d times\n", name, present));
                }
                report.append("\nTotal Students: ").append(total);
                report.append("\nOnline Now: ").append(activeSessions.size());

            } catch (SQLException e) {
                report.append("Error: " + e.getMessage());
            }

            Platform.runLater(() -> reportArea.setText(report.toString()));
        }).start();

        Button closeBtn = new Button("Close");
        closeBtn.setStyle(BUTTON_BLUE);
        closeBtn.setOnAction(e -> dialog.close());

        root.getChildren().addAll(title, reportArea, closeBtn);

        Scene scene = new Scene(root, 600, 500);
        dialog.setScene(scene);
        dialog.show();
    }

    // ============================================================
    // SERVER OPERATIONS
    // ============================================================

    // Start the server socket
    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                log("✅ Server started on port " + PORT);
                log("📡 Waiting for students...");

                while (isRunning) {
                    Socket client = serverSocket.accept();
                    log("📱 New connection from: " + client.getInetAddress());
                    new StudentAuthHandler(client, this).start();
                }
            } catch (IOException e) {
                log("❌ Server error: " + e.getMessage());
            }
        }).start();
    }

    // Animate the status dot
    private void animateStatusDot() {
        Timeline pulse = new Timeline(
                new KeyFrame(Duration.seconds(0.5), e -> serverStatusIndicator.setTextFill(Color.web("#2ecc71"))),
                new KeyFrame(Duration.seconds(1), e -> serverStatusIndicator.setTextFill(Color.web("#27ae60"))));
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    // Update the student list display
    private void updateStudentList() {
        Platform.runLater(() -> {
            studentListView.getItems().clear();
            for (StudentSession s : activeSessions.values()) {
                String status = s.isActive() ? "🟢 Active" : "🔴 Inactive";
                studentListView.getItems().add(status + " - " + s.fullName + " (@" + s.username + ")");
            }
            if (activeSessions.isEmpty()) {
                studentListView.getItems().add("(No students online)");
            }
            connectedCountLabel.setText(String.valueOf(activeSessions.size()));
        });
    }

    // Update online count
    private void updateConnectedCount() {
        Platform.runLater(() -> connectedCountLabel.setText(String.valueOf(activeSessions.size())));
    }

    // Broadcast message to all students
    public void broadcastToAll(String message) {
        log("📡 Broadcasting to " + activeSessions.size() + " students");
        for (StudentSession s : activeSessions.values()) {
            if (s.out != null)
                s.out.println(message);
        }
    }

    // Add a new session
    public void addSession(String token, StudentSession session) {
        activeSessions.put(token, session);
        updateStudentList();
        log("✅ " + session.fullName + " logged in");

        // Record attendance
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT INTO attendance_log (student_id, action, action_time) VALUES (?, 'LOGIN', CURRENT_TIMESTAMP)")) {
                pstmt.setInt(1, session.studentId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                log("Attendance error: " + e.getMessage());
            }
        }).start();
    }

    // Remove a session
    public void removeSession(String token) {
        StudentSession session = activeSessions.remove(token);
        if (session != null) {
            log("👋 " + session.fullName + " logged out");
            updateStudentList();
        }
    }

    // ============================================================
    // DATABASE HELPERS
    // ============================================================
    private String getRegisteredCount() {
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM students WHERE role = 'student'")) {
            if (rs.next())
                return String.valueOf(rs.getInt(1));
        } catch (SQLException e) {
        }
        return "0";
    }

    private String getQuizCount() {
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM quiz_questions")) {
            if (rs.next())
                return String.valueOf(rs.getInt(1));
        } catch (SQLException e) {
        }
        return "0";
    }

    private String getResponseCount() {
        try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM quiz_responses")) {
            if (rs.next())
                return String.valueOf(rs.getInt(1));
        } catch (SQLException e) {
        }
        return "0";
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return password;
        }
    }

    private void markAllPresent() {
        new Thread(() -> {
            try (Connection conn = DatabaseConnection.getConnection();
                    PreparedStatement pstmt = conn.prepareStatement(
                            "INSERT INTO attendance_log (student_id, action) " +
                                    "SELECT student_id, 'MANUAL_PRESENT' FROM students WHERE role = 'student'")) {
                int updated = pstmt.executeUpdate();
                log("✅ Marked " + updated + " students as present");
            } catch (SQLException e) {
                log("Failed to mark attendance: " + e.getMessage());
            }
        }).start();
    }

    private void generateReport() {
        StringBuilder report = new StringBuilder();
        report.append("═══════════════════════════════════════════════════\n");
        report.append("           CLASSROOM MONITORING REPORT\n");
        report.append("═══════════════════════════════════════════════════\n");
        report.append("Generated: ").append(LocalDateTime.now()).append("\n");
        report.append("Online Students: ").append(activeSessions.size()).append("\n");
        report.append("Registered Students: ").append(getRegisteredCount()).append("\n");
        report.append("═══════════════════════════════════════════════════\n");

        showAlert("Report", report.toString());
        log("📊 Report generated");
    }

    // ============================================================
    // UTILITY METHODS
    // ============================================================
    public void log(String message) {
        Platform.runLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.appendText("[" + time + "] " + message + "\n");
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @Override
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null)
                serverSocket.close();
        } catch (IOException e) {
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// ============================================================
// STUDENT AUTH HANDLER - Handles each student connection
// ============================================================
class StudentAuthHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ServerMain server;
    private String sessionToken;
    private int studentId;

    public StudentAuthHandler(Socket socket, ServerMain server) {
        this.socket = socket;
        this.server = server;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            server.log("Error: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            out.println("AUTH_REQUIRED");
            String authMessage = in.readLine();

            if (authMessage != null && authMessage.startsWith("LOGIN:")) {
                String[] parts = authMessage.substring(6).split(":");
                String username = parts[0];
                String password = parts[1];

                if (authenticateStudent(username, password)) {
                    sessionToken = generateToken();
                    ServerMain.StudentSession session = new ServerMain.StudentSession(
                            studentId, username, getStudentName(studentId), sessionToken);
                    session.socket = socket;
                    session.out = out;
                    session.in = in;

                    server.addSession(sessionToken, session);
                    out.println("LOGIN_SUCCESS:" + sessionToken + ":" + session.fullName + ":" + studentId);

                    handleMessages(session);
                } else {
                    out.println("LOGIN_FAILED:Invalid username or password");
                    socket.close();
                }
            } else {
                socket.close();
            }
        } catch (Exception e) {
            server.log("Auth error: " + e.getMessage());
        }
    }

    private boolean authenticateStudent(String username, String password) {
        String hashedPassword = hashPassword(password);
        String sql = "SELECT student_id FROM students WHERE username = ? AND password = ? AND role = 'student'";

        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                studentId = rs.getInt("student_id");
                return true;
            }
        } catch (SQLException e) {
            server.log("Auth error: " + e.getMessage());
        }
        return false;
    }

    private String getStudentName(int id) {
        String sql = "SELECT full_name FROM students WHERE student_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next())
                return rs.getString("full_name");
        } catch (SQLException e) {
        }
        return "Student";
    }

    private void handleMessages(ServerMain.StudentSession session) {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                session.lastHeartbeat = System.currentTimeMillis();

                if (message.equals("HEARTBEAT")) {
                    out.println("HEARTBEAT_OK");
                } else if (message.startsWith("QUIZ_ANSWER:")) {
                    String answer = message.substring(12);
                    server.log("📝 " + session.fullName + " answered: " + answer);
                    out.println("ANSWER_RECEIVED:RECEIVED");
                } else {
                    server.log("📨 From " + session.fullName + ": " + message);
                }
            }
        } catch (IOException e) {
            server.log("Connection lost: " + session.fullName);
        } finally {
            server.removeSession(sessionToken);
            try {
                socket.close();
            } catch (Exception e) {
            }
        }
    }

    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash)
                hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return password;
        }
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}

// ============================================================
// DATABASE CONNECTION (Updated for SQLite)
// ============================================================
class DatabaseConnection {
    // This creates 'classroom.db' in the project folder automatically
    private static final String URL = "jdbc:sqlite:classroom.db";

    static {
        try {
            // Updated for the SQLite driver instead of PostgreSQL
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("SQLite JDBC Driver not found!");
            e.printStackTrace();
        }
    }

    /**
     * Connects to the local SQLite database file.
     * No username or password required.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /**
     * RUN THIS ONCE at server startup to build your tables.
     * This replaces the manual SQL setup we had in PostgreSQL.
     */
    public static void setupDatabase() {
        String studentTable = "CREATE TABLE IF NOT EXISTS students ("
                + "student_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE NOT NULL,"
                + "password TEXT NOT NULL,"
                + "full_name TEXT NOT NULL,"
                + "email TEXT,"
                + "class_name TEXT,"
                + "role TEXT DEFAULT 'student');";

        String logTable = "CREATE TABLE IF NOT EXISTS attendance_log ("
                + "log_id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "student_id INTEGER,"
                + "action TEXT,"
                + "action_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                + "FOREIGN KEY(student_id) REFERENCES students(student_id));";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(studentTable);
            stmt.execute(logTable);
            System.out.println("✅ SQLite Tables checked/created successfully.");
        } catch (SQLException e) {
            System.err.println("Error initializing tables: " + e.getMessage());
        }
    }
}