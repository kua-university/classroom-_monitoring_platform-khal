package studentclientfx;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.prefs.Preferences;
import javafx.application.Application;
import static javafx.application.Application.launch;
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
 * STUDENT CLIENT - Student Portal
 * Connects to teacher server, receives quizzes and announcements
 */
public class StudentClient extends Application {
    
    // ============================================================
    // NETWORK COMPONENTS
    // ============================================================
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private boolean screenLocked = false;
    
    // User data
    private String sessionToken;
    private String studentName;
    private int studentId;
    
    // ============================================================
    // QUIZ DATA
    // ============================================================
    private List<QuestionData> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private boolean quizActive = false;
    private int correctCount = 0;
    private int expectedTotalQuestions = 0;
    
    // ============================================================
    // USER PREFERENCES
    // ============================================================
    private Preferences prefs;
    private boolean showImmediateFeedback = true;
    private boolean showCorrectAnswerPref = false;
    
    // ============================================================
    // UI COMPONENTS
    // ============================================================
    private TextArea chatArea;
    private TextField chatInputField;
    private VBox examPanel;
    private Label questionTextLabel;
    private ToggleGroup answerGroup;
    private RadioButton[] optionButtons;
    private Label questionNumberLabel;
    private Label scorePreviewLabel;
    private Button prevBtn, nextBtn, finishBtn, submitAnswerBtn;
    private ProgressBar progressBar;
    private Label connectionStatusLabel;
    private Label feedbackModeLabel;
    private Label answeredLabel, remainingLabel;
    private Label lockStatusLabel;
    
    // Button styles
    private static final String BUTTON_BLUE = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 8 20; -fx-cursor: hand;";
    private static final String BUTTON_GREEN = "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 8 20; -fx-cursor: hand;";
    private static final String BUTTON_PURPLE = "-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 8 20; -fx-cursor: hand;";
    
    // ============================================================
    // QUESTION DATA MODEL
    // ============================================================
    private static class QuestionData {
        int number;
        String text;
        String optionA, optionB, optionC, optionD;
        String correctAnswer;
        String userAnswer;
        boolean answered;
        
        QuestionData(int num, String text, String a, String b, String c, String d, String correct) {
            this.number = num;
            this.text = text;
            this.optionA = a;
            this.optionB = b;
            this.optionC = c;
            this.optionD = d;
            this.correctAnswer = correct;
            this.userAnswer = "";
            this.answered = false;
        }
        
        String getOptionText(String letter) {
            switch (letter) {
                case "A": return optionA;
                case "B": return optionB;
                case "C": return optionC;
                case "D": return optionD;
                default: return "";
            }
        }
        
        boolean isCorrect() {
            return userAnswer.equalsIgnoreCase(correctAnswer);
        }
    }
    
    // ============================================================
    // APPLICATION ENTRY POINT
    // ============================================================
    @Override
    public void start(Stage stage) {
        prefs = Preferences.userNodeForPackage(StudentClient.class);
        showImmediateFeedback = prefs.getBoolean("showImmediateFeedback", true);
        showCorrectAnswerPref = prefs.getBoolean("showCorrectAnswer", false);
        showLoginScreen(stage);
    }
    
    // ============================================================
    // LOGIN SCREEN
    // ============================================================
    private void showLoginScreen(Stage stage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #f0f4f8, #e2e8f0);");
        
        VBox centerBox = new VBox(25);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(40));
        
        // Logo
        StackPane logoPane = new StackPane();
        logoPane.setStyle("-fx-background-color: #e94560; -fx-background-radius: 30;");
        logoPane.setPadding(new Insets(20, 30, 20, 30));
        Label logoLabel = new Label("🎓");
        logoLabel.setFont(Font.font(48));
        logoPane.getChildren().add(logoLabel);
        
        // Title
        Label titleLabel = new Label("Classroom Monitor");
        titleLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.web("#2c3e50"));
        
        Label subtitleLabel = new Label("Student Portal");
        subtitleLabel.setFont(Font.font("Poppins", 14));
        subtitleLabel.setTextFill(Color.web("#7f8c8d"));
        
        // Login form
        VBox formBox = new VBox(15);
        formBox.setMaxWidth(380);
        formBox.setStyle("-fx-background-color: white; -fx-background-radius: 20;");
        formBox.setPadding(new Insets(30));
        
        Label formTitle = new Label("Welcome Back!");
        formTitle.setFont(Font.font("Poppins", FontWeight.BOLD, 20));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");
        usernameField.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-radius: 12; -fx-padding: 12 15;");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-radius: 12; -fx-padding: 12 15;");
        
        Button loginBtn = new Button("Login");
        loginBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 12; -fx-cursor: hand;");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        
        Label messageLabel = new Label();
        messageLabel.setTextFill(Color.web("#e74c3c"));
        
        Hyperlink registerLink = new Hyperlink();
        registerLink.setTextFill(Color.web("#3498db"));
        
        Button settingsBtn = new Button("⚙ Settings");
        settingsBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 8 20; -fx-cursor: hand;");
        settingsBtn.setMaxWidth(Double.MAX_VALUE);
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        formBox.getChildren().addAll(formTitle, usernameField, passwordField,
                                     loginBtn, settingsBtn, messageLabel, registerLink);
        centerBox.getChildren().addAll(logoPane, titleLabel, subtitleLabel, formBox);
        
        mainLayout.setCenter(centerBox);
        
        Scene scene = new Scene(mainLayout, 900, 750);
        stage.setScene(scene);
        stage.setTitle("Student Login - Classroom Monitor");
        stage.show();
        
        // Login action
        loginBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            
            if (username.isEmpty() || password.isEmpty()) {
                messageLabel.setText("Please enter username and password");
                return;
            }
            
            loginBtn.setDisable(true);
            loginBtn.setText("Connecting...");
            
            new Thread(() -> {
                try {
                    socket = new Socket("localhost", 9010);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    
                    String authResponse = in.readLine();
                    if ("AUTH_REQUIRED".equals(authResponse)) {
                        out.println("LOGIN:" + username + ":" + password);
                        String response = in.readLine();
                        
                        if (response.startsWith("LOGIN_SUCCESS:")) {
                            String[] parts = response.split(":");
                            sessionToken = parts[1];
                            studentName = parts[2];
                            studentId = Integer.parseInt(parts[3]);
                            
                            out.println("ATTENDANCE:PRESENT");
                            
                            Platform.runLater(() -> {
                                messageLabel.setTextFill(Color.web("#27ae60"));
                                messageLabel.setText("Login successful!");
                                showMainDashboard(stage);
                            });
                        } else {
                            Platform.runLater(() -> {
                                messageLabel.setText("Invalid username or password");
                                loginBtn.setDisable(false);
                                loginBtn.setText("Login");
                            });
                            socket.close();
                        }
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        messageLabel.setText("Cannot connect to server. Make sure teacher is online.");
                        loginBtn.setDisable(false);
                        loginBtn.setText("Login");
                    });
                }
            }).start();
        });
        
        registerLink.setOnAction(e -> showRegisterScreen(stage));
    }
    
    // ============================================================
    // SETTINGS DIALOG
    // ============================================================
    private void showSettingsDialog() {
        Stage dialog = new Stage();
        dialog.setTitle("Quiz Preferences");
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        
        VBox root = new VBox(20);
        root.setPadding(new Insets(25));
        root.setStyle("-fx-background-color: white; -fx-background-radius: 15;");
        
        Label title = new Label("⚙ Quiz Preferences");
        title.setFont(Font.font("Poppins", FontWeight.BOLD, 20));
        
        Label desc = new Label("Customize how you receive quiz feedback");
        desc.setFont(Font.font("Poppins", 12));
        desc.setTextFill(Color.web("#7f8c8d"));
        
        VBox options = new VBox(15);
        options.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 15; -fx-padding: 20;");
        
        CheckBox immediateCheck = new CheckBox("Show immediate feedback after each question");
        immediateCheck.setSelected(showImmediateFeedback);
        
        CheckBox answerCheck = new CheckBox("Show correct answer for incorrect responses");
        answerCheck.setSelected(showCorrectAnswerPref);
        
        options.getChildren().addAll(immediateCheck, answerCheck);
        
        VBox infoBox = new VBox(8);
        infoBox.setStyle("-fx-background-color: #e8f0fe; -fx-background-radius: 10; -fx-padding: 12;");
        Label infoTitle = new Label("ℹ️ How it works:");
        infoTitle.setFont(Font.font("Poppins", FontWeight.BOLD, 12));
        infoTitle.setTextFill(Color.web("#3498db"));
        
        Label infoText = new Label(
            "• With immediate feedback ON: See results after each question\n" +
            "• With immediate feedback OFF: All results shown at the end\n" +
            "• Teacher can lock the exam panel during tests"
        );
        infoText.setFont(Font.font("Poppins", 11));
        infoText.setWrapText(true);
        infoBox.getChildren().addAll(infoTitle, infoText);
        
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button saveBtn = new Button("Save Preferences");
        saveBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 10 25; -fx-cursor: hand;");
        
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 10 25; -fx-cursor: hand;");
        
        buttonBox.getChildren().addAll(saveBtn, cancelBtn);
        
        saveBtn.setOnAction(e -> {
            showImmediateFeedback = immediateCheck.isSelected();
            showCorrectAnswerPref = answerCheck.isSelected();
            prefs.putBoolean("showImmediateFeedback", showImmediateFeedback);
            prefs.putBoolean("showCorrectAnswer", showCorrectAnswerPref);
            
            if (feedbackModeLabel != null) {
                feedbackModeLabel.setText(showImmediateFeedback ? "🔔 Immediate" : "📋 At the end");
            }
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Preferences Saved");
            alert.setContentText("Your quiz preferences have been saved!");
            alert.showAndWait();
            dialog.close();
        });
        
        cancelBtn.setOnAction(e -> dialog.close());
        
        root.getChildren().addAll(title, desc, options, infoBox, buttonBox);
        
        Scene scene = new Scene(root, 500, 550);
        dialog.setScene(scene);
        dialog.show();
    }
    
    // ============================================================
    // REGISTRATION SCREEN
    // ============================================================
    private void showRegisterScreen(Stage stage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #f0f4f8, #e2e8f0);");
        
        VBox centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(40));
        
        Label titleLabel = new Label("🎓 Create Account");
        titleLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 26));
        
        VBox formBox = new VBox(12);
        formBox.setMaxWidth(400);
        formBox.setStyle("-fx-background-color: white; -fx-background-radius: 20;");
        formBox.setPadding(new Insets(30));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username *");
        
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password *");
        
        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full Name *");
        
        TextField emailField = new TextField();
        emailField.setPromptText("Email (optional)");
        
        TextField classField = new TextField();
        classField.setPromptText("Class (optional)");
        
        Button registerBtn = new Button("Register");
        registerBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 25; -fx-padding: 12; -fx-cursor: hand;");
        registerBtn.setMaxWidth(Double.MAX_VALUE);
        
        Label messageLabel = new Label();
        messageLabel.setTextFill(Color.web("#e74c3c"));
        
        Hyperlink loginLink = new Hyperlink("Already have an account? Login here");
        loginLink.setTextFill(Color.web("#3498db"));
        
        formBox.getChildren().addAll(usernameField, passwordField, fullNameField,
                                     emailField, classField, registerBtn, messageLabel, loginLink);
        centerBox.getChildren().addAll(titleLabel, formBox);
        
        mainLayout.setCenter(centerBox);
        
        Scene scene = new Scene(mainLayout, 900, 750);
        stage.setScene(scene);
        stage.setTitle("Student Registration");
        
        registerBtn.setOnAction(e -> {
            String username = usernameField.getText().trim();
            String password = passwordField.getText();
            String fullName = fullNameField.getText().trim();
            
            if (username.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
                messageLabel.setText("Please fill all required fields (*)");
                return;
            }
            
            registerBtn.setDisable(true);
            registerBtn.setText("Registering...");
            
            new Thread(() -> {
                try {
                    socket = new Socket("localhost", 8888);
                    out = new PrintWriter(socket.getOutputStream(), true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    
                    String authResponse = in.readLine();
                    if ("AUTH_REQUIRED".equals(authResponse)) {
                        String email = emailField.getText().trim();
                        String className = classField.getText().trim();
                        out.println("REGISTER:" + username + ":" + password + ":" + fullName + ":" + email + ":" + className);
                        String response = in.readLine();
                        
                        if ("REGISTER_SUCCESS".equals(response)) {
                            Platform.runLater(() -> {
                                messageLabel.setTextFill(Color.web("#27ae60"));
                                messageLabel.setText("Registration successful! Please login.");
                                showLoginScreen(stage);
                            });
                        } else {
                            Platform.runLater(() -> {
                                messageLabel.setText("Username already exists!");
                                registerBtn.setDisable(false);
                                registerBtn.setText("Register");
                            });
                        }
                    }
                    socket.close();
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        messageLabel.setText("Cannot connect to server");
                        registerBtn.setDisable(false);
                        registerBtn.setText("Register");
                    });
                }
            }).start();
        });
        
        loginLink.setOnAction(e -> showLoginScreen(stage));
    }
    
    // ============================================================
    // MAIN DASHBOARD (After Login)
    // ============================================================
    private void showMainDashboard(Stage stage) {
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: linear-gradient(to bottom right, #f0f4f8, #e2e8f0);");
        
        VBox header = createHeader();
        
        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent;");
        
        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-radius: 12;");
        chatArea.setWrapText(true);
        chatArea.setPrefWidth(500);
        
        examPanel = createExamPanel();
        
        splitPane.getItems().addAll(chatArea, examPanel);
        splitPane.setDividerPositions(0.5);
        
        HBox inputBox = createInputArea();
        VBox sidebar = createSidebar();
        
        HBox mainContent = new HBox(15);
        mainContent.setPadding(new Insets(15));
        mainContent.getChildren().addAll(splitPane, sidebar);
        HBox.setHgrow(splitPane, Priority.ALWAYS);
        
        VBox topSection = new VBox(0, header);
        
        mainLayout.setTop(topSection);
        mainLayout.setCenter(mainContent);
        mainLayout.setBottom(inputBox);
        
        Scene scene = new Scene(mainLayout, 1400, 800);
        stage.setScene(scene);
        stage.setTitle("Student Portal - " + studentName);
        stage.setOnCloseRequest(e -> disconnect());
        
        addSystemMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        addSystemMessage("🎉 Welcome " + studentName + "!");
        addSystemMessage("✅ Successfully connected to the classroom.");
        addSystemMessage("📝 When teacher starts a quiz, it will appear on the right.");
        addSystemMessage("🔒 Teacher can lock the exam panel during tests.");
        addSystemMessage("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        startMessageListener();
        startHeartbeat();
    }
    
    // Header with student info
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setPadding(new Insets(15, 25, 15, 25));
        header.setStyle("-fx-background-color: linear-gradient(to right, #1a1a2e, #16213e);");
        
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.setSpacing(20);
        
        StackPane avatar = new StackPane();
        avatar.setStyle("-fx-background-color: #e94560; -fx-background-radius: 12;");
        avatar.setPadding(new Insets(8, 12, 8, 12));
        Label avatarLabel = new Label("👨‍🎓");
        avatarLabel.setFont(Font.font(22));
        avatar.getChildren().add(avatarLabel);
        
        VBox titleBox = new VBox(3);
        Label titleLabel = new Label("Student Portal");
        titleLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);
        
        Label nameLabel = new Label(studentName);
        nameLabel.setFont(Font.font("Poppins", 11));
        nameLabel.setTextFill(Color.web("#a8b2d1"));
        titleBox.getChildren().addAll(titleLabel, nameLabel);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        HBox statusBox = new HBox(8);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setStyle("-fx-background-color: rgba(255,255,255,0.1); -fx-background-radius: 20; -fx-padding: 6 15;");
        
        Label dot = new Label("●");
        dot.setFont(Font.font(12));
        dot.setTextFill(Color.web("#2ecc71"));
        
        connectionStatusLabel = new Label("Connected");
        connectionStatusLabel.setFont(Font.font("Poppins", 11));
        connectionStatusLabel.setTextFill(Color.WHITE);
        
        lockStatusLabel = new Label("🔓");
        lockStatusLabel.setFont(Font.font(14));
        lockStatusLabel.setTextFill(Color.web("#27ae60"));
        
        statusBox.getChildren().addAll(dot, connectionStatusLabel, lockStatusLabel);
        
        Button settingsBtn = new Button("⚙");
        settingsBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 6 12; -fx-cursor: hand;");
        settingsBtn.setOnAction(e -> showSettingsDialog());
        
        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 6 18; -fx-cursor: hand;");
        logoutBtn.setOnAction(e -> {
            disconnect();
            showLoginScreen((Stage) logoutBtn.getScene().getWindow());
        });
        
        topRow.getChildren().addAll(avatar, titleBox, spacer, statusBox, settingsBtn, logoutBtn);
        header.getChildren().add(topRow);
        return header;
    }
    
    // Exam panel (quiz taking area)
    private VBox createExamPanel() {
        VBox panel = new VBox(15);
        panel.setPadding(new Insets(20));
        panel.setStyle("-fx-background-color: white; -fx-background-radius: 15; -fx-border-color: #e0e0e0;");
        panel.setVisible(false);
        
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(10);
        
        Label examTitle = new Label("📝 QUIZ EXAMINATION");
        examTitle.setFont(Font.font("Poppins", FontWeight.BOLD, 18));
        examTitle.setTextFill(Color.web("#e94560"));
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setStyle("-fx-accent: #27ae60;");
        
        header.getChildren().addAll(examTitle, spacer, progressBar);
        
        questionNumberLabel = new Label("Question 1 of 0");
        questionNumberLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 14));
        
        questionTextLabel = new Label();
        questionTextLabel.setWrapText(true);
        questionTextLabel.setStyle("-fx-background-color: #f8f9fa; -fx-padding: 15; -fx-background-radius: 10;");
        
        TitledPane optionsPane = new TitledPane("Select Your Answer", createOptionsPanel());
        optionsPane.setExpanded(true);
        
        HBox navBox = new HBox(15);
        navBox.setAlignment(Pos.CENTER);
        
        prevBtn = new Button("◀ PREVIOUS");
        prevBtn.setStyle(BUTTON_BLUE);
        
        submitAnswerBtn = new Button("✓ SUBMIT ANSWER");
        submitAnswerBtn.setStyle(BUTTON_PURPLE);
        
        nextBtn = new Button("NEXT ▶");
        nextBtn.setStyle(BUTTON_BLUE);
        
        finishBtn = new Button("🏆 FINISH QUIZ");
        finishBtn.setStyle(BUTTON_GREEN);
        
        navBox.getChildren().addAll(prevBtn, submitAnswerBtn, nextBtn, finishBtn);
        
        prevBtn.setOnAction(e -> navigatePrevious());
        nextBtn.setOnAction(e -> navigateNext());
        finishBtn.setOnAction(e -> finishQuiz());
        submitAnswerBtn.setOnAction(e -> submitCurrentAnswer());
        
        scorePreviewLabel = new Label("📊 Progress: 0/0 questions answered");
        scorePreviewLabel.setFont(Font.font("Poppins", 11));
        scorePreviewLabel.setTextFill(Color.web("#7f8c8d"));
        
        panel.getChildren().addAll(header, questionNumberLabel, questionTextLabel,
                                   optionsPane, navBox, scorePreviewLabel);
        return panel;
    }
    
    // Options panel with radio buttons
    private VBox createOptionsPanel() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(15));
        
        answerGroup = new ToggleGroup();
        optionButtons = new RadioButton[4];
        String[] letters = {"A", "B", "C", "D"};
        String[] colors = {"#3498db", "#2ecc71", "#f39c12", "#e74c3c"};
        
        for (int i = 0; i < 4; i++) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            
            Label letterLabel = new Label(letters[i] + ")");
            letterLabel.setFont(Font.font("Poppins", FontWeight.BOLD, 14));
            letterLabel.setTextFill(Color.web(colors[i]));
            letterLabel.setPrefWidth(30);
            
            optionButtons[i] = new RadioButton();
            optionButtons[i].setToggleGroup(answerGroup);
            optionButtons[i].setFont(Font.font("Poppins", 13));
            
            row.getChildren().addAll(letterLabel, optionButtons[i]);
            box.getChildren().add(row);
        }
        
        return box;
    }
    
    // Input area for sending messages
    private HBox createInputArea() {
        HBox inputBox = new HBox(12);
        inputBox.setPadding(new Insets(15, 20, 20, 20));
        inputBox.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        
        chatInputField = new TextField();
        chatInputField.setPromptText("Type message or 'quiz A' to answer...");
        chatInputField.setStyle("-fx-background-color: #f8f9fa; -fx-border-color: #e0e0e0; -fx-border-radius: 25; -fx-padding: 12 18;");
        HBox.setHgrow(chatInputField, Priority.ALWAYS);
        
        Button sendBtn = new Button("Send");
        sendBtn.setStyle(BUTTON_BLUE);
        sendBtn.setOnAction(e -> sendMessage());
        chatInputField.setOnAction(e -> sendMessage());
        
        inputBox.getChildren().addAll(chatInputField, sendBtn);
        return inputBox;
    }
    
    // Right sidebar with info
    private VBox createSidebar() {
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(20));
        sidebar.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 0 1;");
        sidebar.setPrefWidth(220);
        
        Label infoTitle = new Label("📌 Quiz Information");
        infoTitle.setFont(Font.font("Poppins", FontWeight.BOLD, 13));
        
        answeredLabel = new Label("Answered: 0");
        answeredLabel.setFont(Font.font("Poppins", 11));
        
        remainingLabel = new Label("Remaining: 0");
        remainingLabel.setFont(Font.font("Poppins", 11));
        
        Separator sep1 = new Separator();
        
        Label lockInfo = new Label("🔒 Exam Status:");
        lockInfo.setFont(Font.font("Poppins", FontWeight.BOLD, 13));
        
        Label lockValue = new Label("🔓 Unlocked");
        lockValue.setFont(Font.font("Poppins", 11));
        lockValue.setTextFill(Color.web("#27ae60"));
        
        Separator sep2 = new Separator();
        
        Label feedbackTitle = new Label("⚙ Feedback Mode");
        feedbackTitle.setFont(Font.font("Poppins", FontWeight.BOLD, 13));
        
        feedbackModeLabel = new Label(showImmediateFeedback ? "🔔 Immediate" : "📋 At the end");
        feedbackModeLabel.setFont(Font.font("Poppins", 11));
        
        Separator sep3 = new Separator();
        
        Label tipsTitle = new Label("💡 Tips");
        tipsTitle.setFont(Font.font("Poppins", FontWeight.BOLD, 13));
        
        VBox tipsBox = new VBox(10);
        String[] tips = {
            "📝 Use exam panel on right",
            "◀ ▶ Navigate questions",
            "✓ Submit before moving on",
            "🏆 Click FINISH to complete",
            "🔒 Teacher can lock exam"
        };
        for (String tip : tips) {
            Label tipLabel = new Label(tip);
            tipLabel.setFont(Font.font("Poppins", 10));
            tipLabel.setTextFill(Color.web("#7f8c8d"));
            tipsBox.getChildren().add(tipLabel);
        }
        
        sidebar.getChildren().addAll(infoTitle, answeredLabel, remainingLabel, sep1,
                                     lockInfo, lockValue, sep2, feedbackTitle, feedbackModeLabel,
                                     sep3, tipsTitle, tipsBox);
        return sidebar;
    }
    
    // ============================================================
    // NETWORK COMMUNICATION
    // ============================================================
    
    private void startMessageListener() {
        new Thread(() -> {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("📨 Received: " + message);
                    final String msg = message;
                    Platform.runLater(() -> handleMessage(msg));
                }
            } catch (IOException e) {
                Platform.runLater(() -> {
                    addSystemMessage("❌ Connection lost to teacher!");
                    connectionStatusLabel.setText("Disconnected");
                    chatInputField.setDisable(true);
                });
            }
        }).start();
    }
    
    private void startHeartbeat() {
        new Thread(() -> {
            while (connected) {
                try {
                    Thread.sleep(5000);
                    if (out != null) out.println("HEARTBEAT");
                } catch (InterruptedException e) { break; }
            }
        }).start();
    }
    
    // Process incoming messages from teacher
    private void handleMessage(String message) {
        if (message.startsWith("ANNOUNCEMENT:")) {
            addAnnouncement(message.substring(13));
        }
        else if (message.equals("LOCK_SCREEN")) {
            screenLocked = true;
            chatInputField.setDisable(true);
            
            examPanel.setDisable(true);
            examPanel.setStyle("-fx-opacity: 0.5; -fx-background-color: #e0e0e0; -fx-border-color: #e74c3c; -fx-border-width: 2;");
            
            prevBtn.setDisable(true);
            nextBtn.setDisable(true);
            submitAnswerBtn.setDisable(true);
            finishBtn.setDisable(true);
            for (RadioButton rb : optionButtons) rb.setDisable(true);
            
            lockStatusLabel.setText("🔒");
            lockStatusLabel.setTextFill(Color.web("#e74c3c"));
            
            addSystemMessage("🔒 SCREEN LOCKED by teacher - Exam panel disabled!");
            
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Screen Locked");
            alert.setHeaderText("🔒 Screen Locked by Teacher");
            alert.setContentText("The exam is locked. You cannot answer questions until unlocked.");
            alert.showAndWait();
        }
        else if (message.equals("UNLOCK_SCREEN")) {
            screenLocked = false;
            chatInputField.setDisable(false);
            
            examPanel.setDisable(false);
            examPanel.setStyle("-fx-opacity: 1; -fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 1;");
            
            if (questions.size() > 0) {
                prevBtn.setDisable(currentQuestionIndex == 0);
                nextBtn.setDisable(currentQuestionIndex >= questions.size() - 1);
            }
            submitAnswerBtn.setDisable(false);
            finishBtn.setDisable(false);
            for (RadioButton rb : optionButtons) rb.setDisable(false);
            
            lockStatusLabel.setText("🔓");
            lockStatusLabel.setTextFill(Color.web("#27ae60"));
            
            addSystemMessage("🔓 SCREEN UNLOCKED - Exam panel enabled");
        }
        else if (message.startsWith("QUIZ:")) {
            parseQuizMessage(message);
        }
        else if (message.equals("QUIZ_COMPLETE")) {
            addSystemMessage("🎉 The quiz has been completed!");
        }
        else if (message.startsWith("ANSWER_RECEIVED:")) {
            if (showImmediateFeedback) {
                addSystemMessage("✅ Your answer was recorded!");
            }
        }
    }
    
    // Parse quiz message from teacher
    private void parseQuizMessage(String message) {
        try {
            String[] parts = message.substring(5).split("\\|");
            String qInfo = parts[0];
            String question = parts[1];
            String optA = parts[2].substring(2);
            String optB = parts[3].substring(2);
            String optC = parts[4].substring(2);
            String optD = parts[5].substring(2);
            String correctAnswer = parts[6].substring(8);
            
            String[] qParts = qInfo.split("/");
            int qNum = Integer.parseInt(qParts[0]);
            int total = Integer.parseInt(qParts[1]);
            
            expectedTotalQuestions = total;
            
            if (!quizActive || questions.isEmpty()) {
                questions.clear();
                correctCount = 0;
                quizActive = true;
                currentQuestionIndex = 0;
                Platform.runLater(() -> {
                    examPanel.setVisible(true);
                    addSystemMessage("📝 Quiz started! Total questions: " + total);
                });
            }
            
            boolean exists = false;
            for (QuestionData qd : questions) {
                if (qd.number == qNum) { exists = true; break; }
            }
            
            if (!exists) {
                QuestionData newQuestion = new QuestionData(qNum, question, optA, optB, optC, optD, correctAnswer);
                questions.add(newQuestion);
                questions.sort(Comparator.comparingInt(qd -> qd.number));
            }
            
            Platform.runLater(() -> {
                displayCurrentQuestion();
                updateProgress();
            });
            
            if (questions.size() == expectedTotalQuestions) {
                Platform.runLater(() -> addSystemMessage("✅ All " + expectedTotalQuestions + " questions loaded!"));
            }
            
        } catch (Exception e) {
            System.err.println("Parse error: " + e.getMessage());
        }
    }
    
    // Display the current question in exam panel
    private void displayCurrentQuestion() {
        if (questions.isEmpty()) return;
        if (currentQuestionIndex < 0) currentQuestionIndex = 0;
        if (currentQuestionIndex >= questions.size()) currentQuestionIndex = questions.size() - 1;
        
        QuestionData q = questions.get(currentQuestionIndex);
        
        Platform.runLater(() -> {
            questionNumberLabel.setText("Question " + (currentQuestionIndex + 1) + " of " + expectedTotalQuestions);
            questionTextLabel.setText(q.text);
            
            optionButtons[0].setText(q.optionA);
            optionButtons[1].setText(q.optionB);
            optionButtons[2].setText(q.optionC);
            optionButtons[3].setText(q.optionD);
            
            answerGroup.selectToggle(null);
            if (!q.userAnswer.isEmpty()) {
                int index = getOptionIndex(q.userAnswer);
                if (index >= 0) optionButtons[index].setSelected(true);
            }
            
            if (!screenLocked) {
                prevBtn.setDisable(currentQuestionIndex == 0);
                nextBtn.setDisable(currentQuestionIndex >= questions.size() - 1);
            }
            
            updateProgress();
        });
    }
    
    private int getOptionIndex(String letter) {
        switch (letter.toUpperCase()) {
            case "A": return 0;
            case "B": return 1;
            case "C": return 2;
            case "D": return 3;
            default: return -1;
        }
    }
    
    private String getLetterForIndex(int index) {
        switch (index) {
            case 0: return "A";
            case 1: return "B";
            case 2: return "C";
            case 3: return "D";
            default: return "";
        }
    }
    
    private void navigatePrevious() {
        if (currentQuestionIndex > 0 && !screenLocked) {
            currentQuestionIndex--;
            displayCurrentQuestion();
            addSystemMessage("📖 Moved to Question " + (currentQuestionIndex + 1));
        }
    }
    
    private void navigateNext() {
        if (currentQuestionIndex < questions.size() - 1 && !screenLocked) {
            currentQuestionIndex++;
            displayCurrentQuestion();
            addSystemMessage("📖 Moved to Question " + (currentQuestionIndex + 1));
        }
    }
    
    private void submitCurrentAnswer() {
        if (screenLocked) {
            addSystemMessage("🔒 Screen is locked! Cannot submit answers.");
            return;
        }
        if (questions.isEmpty()) {
            addSystemMessage("⚠️ No active quiz found!");
            return;
        }
        if (currentQuestionIndex >= questions.size()) {
            addSystemMessage("⚠️ Invalid question index!");
            return;
        }
        
        QuestionData q = questions.get(currentQuestionIndex);
        Toggle selected = answerGroup.getSelectedToggle();
        
        if (selected == null) {
            addSystemMessage("⚠️ Please select an answer before submitting!");
            return;
        }
        
        String answer = "";
        for (int i = 0; i < optionButtons.length; i++) {
            if (optionButtons[i].isSelected()) {
                answer = getLetterForIndex(i);
                break;
            }
        }
        
        if (!answer.isEmpty()) {
            q.userAnswer = answer;
            q.answered = true;
            out.println("QUIZ_ANSWER:" + answer);
            
            if (showImmediateFeedback) {
                if (q.isCorrect()) {
                    correctCount++;
                    addSystemMessage("✅ Question " + q.number + ": Correct!");
                } else {
                    if (showCorrectAnswerPref) {
                        addSystemMessage("❌ Question " + q.number + ": Incorrect. Correct answer is " +
                                        q.correctAnswer + ") " + q.getOptionText(q.correctAnswer));
                    } else {
                        addSystemMessage("❌ Question " + q.number + ": Incorrect.");
                    }
                }
            }
            addSystemMessage("📝 Answer saved for Question " + q.number);
            updateProgress();
        }
    }
    
    private void finishQuiz() {
        if (screenLocked) {
            addSystemMessage("🔒 Screen is locked! Cannot finish quiz.");
            return;
        }
        if (questions.isEmpty()) {
            addSystemMessage("⚠️ No active quiz to finish!");
            return;
        }
        
        if (questions.size() < expectedTotalQuestions) {
            addSystemMessage("⚠️ Waiting for all questions... (" + questions.size() + "/" + expectedTotalQuestions + ")");
            return;
        }
        
        int answeredCount = 0;
        for (QuestionData q : questions) if (!q.userAnswer.isEmpty()) answeredCount++;
        
        if (answeredCount < questions.size()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Finish Quiz");
            confirm.setHeaderText("You have " + (questions.size() - answeredCount) + " unanswered questions!");
            confirm.setContentText("Continue? Unanswered will be marked incorrect.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() != ButtonType.OK) return;
        }
        
        correctCount = 0;
        for (QuestionData q : questions) if (q.isCorrect()) correctCount++;
        
        int percentage = (correctCount * 100) / questions.size();
        String grade = getGrade(percentage);
        
        for (QuestionData q : questions) {
            if (!q.userAnswer.isEmpty()) {
                out.println("QUIZ_ANSWER_FINAL:" + q.number + ":" + q.userAnswer);
            }
        }
        
        showFinalResults(correctCount, questions.size(), percentage, grade);
        quizActive = false;
        examPanel.setVisible(false);
    }
    
    private String getGrade(int percentage) {
        if (percentage >= 90) return "A+ (Excellent!)";
        if (percentage >= 80) return "A (Very Good!)";
        if (percentage >= 70) return "B (Good!)";
        if (percentage >= 60) return "C (Satisfactory)";
        if (percentage >= 50) return "D (Needs Improvement)";
        return "F (Poor)";
    }
    
    private void showFinalResults(int correct, int total, int percentage, String grade) {
        StringBuilder results = new StringBuilder();
        results.append("\n╔════════════════════════════════════════════════════════════╗\n");
        results.append("║                   🏆 QUIZ RESULTS 🏆                       ║\n");
        results.append("╠════════════════════════════════════════════════════════════╣\n");
        results.append(String.format("║  Total Questions: %-43d║\n", total));
        results.append(String.format("║  ✅ Correct: %-49d║\n", correct));
        results.append(String.format("║  ❌ Incorrect: %-47d║\n", total - correct));
        results.append("╠════════════════════════════════════════════════════════════╣\n");
        results.append(String.format("║  📊 Score: %d/%d (%d%%)%35s║\n", correct, total, percentage, " "));
        results.append(String.format("║  🎓 Grade: %-53s║\n", grade));
        results.append("╚════════════════════════════════════════════════════════════╝\n");
        
        chatArea.appendText(results.toString());
        
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Quiz Complete!");
        alert.setHeaderText("🏆 Your Quiz Results");
        alert.setContentText(String.format("Score: %d/%d (%d%%) - %s", correct, total, percentage, grade));
        alert.showAndWait();
    }
    
    // Update progress display - FIXED VERSION
    private void updateProgress() {
        int answered = 0;
        for (QuestionData q : questions) {
            if (!q.userAnswer.isEmpty()) answered++;
        }
        
        int totalQ = expectedTotalQuestions;
        int answeredCount = answered;
        int remainingCount = totalQ - answered;
        double progress = totalQ > 0 ? (double) answered / totalQ : 0;
        
        Platform.runLater(() -> {
            progressBar.setProgress(progress);
            scorePreviewLabel.setText("📊 Progress: " + answeredCount + "/" + totalQ + " questions answered");
            answeredLabel.setText("Answered: " + answeredCount);
            remainingLabel.setText("Remaining: " + remainingCount);
        });
    }
    
    // Send message (chat or quiz answer)
    private void sendMessage() {
        if (screenLocked) {
            addSystemMessage("🔒 Screen is locked! Cannot send messages.");
            return;
        }
        
        String input = chatInputField.getText().trim();
        if (input.isEmpty()) return;
        chatInputField.clear();
        
        if (input.startsWith("quiz ")) {
            String answer = input.substring(5).toUpperCase();
            if (answer.matches("[ABCD]")) {
                if (quizActive && currentQuestionIndex < questions.size()) {
                    questions.get(currentQuestionIndex).userAnswer = answer;
                    out.println("QUIZ_ANSWER:" + answer);
                    addSelfMessage("Submitted answer: " + answer);
                    updateProgress();
                } else {
                    out.println("QUIZ_ANSWER:" + answer);
                    addSelfMessage("Submitted answer: " + answer);
                }
            } else {
                addSystemMessage("Invalid! Use: quiz A, B, C, or D");
            }
        } else {
            out.println(input);
            addSelfMessage(input);
        }
    }
    
    // UI message methods
    private void addAnnouncement(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> {
            chatArea.appendText("\n┌─────────────────────────────────────────────────────────────┐\n");
            chatArea.appendText("│  🔔 [" + timestamp + "] ANNOUNCEMENT\n");
            chatArea.appendText("├─────────────────────────────────────────────────────────────┤\n");
            chatArea.appendText("│  " + message + "\n");
            chatArea.appendText("└─────────────────────────────────────────────────────────────┘\n\n");
        });
    }
    
    private void addSystemMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> chatArea.appendText("[" + timestamp + "] ℹ️ " + message + "\n"));
    }
    
    private void addSelfMessage(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() -> chatArea.appendText("[" + timestamp + "] 👨‍🎓 You: " + message + "\n"));
    }
    
    private void disconnect() {
        connected = false;
        try { if (socket != null) socket.close(); } catch (Exception e) {}
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}