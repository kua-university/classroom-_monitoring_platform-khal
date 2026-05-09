# 🎓 Classroom Monitoring Platform

**Author:** Kalid Jibril | Software Engineer |  
**Course:** SENG5232 – Software Architecture and Design

---

## Project Description

A desktop application that allows a teacher to create a real‑time classroom session and monitor connected students from a single dashboard. It supports live attendance tracking, broadcast announcements, screen lock/unlock, multi‑question quizzes with instant or deferred feedback, and automatic disconnection detection. The system adopts a hybrid desktop‑file architecture: JavaFX for the graphical interface, Java sockets (TCP for commands and UDP for heartbeat) for communication, and an embedded SQLite database that requires no external server, no internet access, and no manual configuration.

---

## Technologies

- **Presentation:** JavaFX 17
- **Communication:** Java Sockets (TCP for commands, UDP for heartbeat)
- **Persistence:** SQLite (embedded via `sqlite-jdbc-3.53.0.0.jar`)
- **Business Logic:** Plain Java (POJOs)

---

## Requirements

- Java JDK 17 or higher with JavaFX support (e.g., Liberica JDK, ZuluFX, or any JDK + OpenJFX)
- The SQLite JDBC driver is already included in the `dist/lib` folder after building
- NetBeans IDE (recommended) or any Java IDE

---

## How to Build and Run

### 1. Open the project in NetBeans
- **File → Open Project** → select the project folder.
- Ensure the SQLite driver is added to Libraries (Project Properties → Libraries → Add JAR/Folder → select `sqlite-jdbc-3.53.0.0.jar`).

### 2. Build the JAR
- Right‑click the project → **Clean and Build**.
- After `BUILD SUCCESSFUL`, the JAR and libraries appear in the `dist/` folder.

### 3. Run the teacher server
- Inside NetBeans: Right‑click `ServerMain.java` → **Run File**.
- Or from the command line (in the project folder):
  ```cmd
  java -cp "dist\Classroom Monitoring Platform.jar;dist\lib\*" classroomserver.ServerMain
--- 
