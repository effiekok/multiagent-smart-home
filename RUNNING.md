# 🚀 How to Run — Multi-Agent Smart Home System

Complete step-by-step guide to run this project from scratch.

---

## 1. Prerequisites

You need the following installed on your machine:

### Java JDK (version 8 or higher)

**macOS (Homebrew):**
```bash
brew install openjdk@11
```

**Windows:** Download from https://adoptium.net/ and run the installer.

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install openjdk-11-jdk
```

**Verify installation:**
```bash
java -version
# Expected output example:
# openjdk version "11.0.x" ...
```

---

### Gradle (version 7 or higher)

You do NOT need to install Gradle separately if you use the Gradle Wrapper (recommended).
The wrapper (`./gradlew`) is included in the repository and will download the correct Gradle version automatically.

If you want to install Gradle manually:
- **macOS:** `brew install gradle`
- **Windows:** Download from https://gradle.org/install/
- **Linux:** `sdk install gradle` (using SDKMAN)

---

## 2. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/multi-agent-smart-home.git
cd multi-agent-smart-home
```

---

## 3. Project Structure Check

After cloning, your directory should look like this:

```
multi-agent-smart-home/
├── smart_home_project.mas2j
├── src/
│   └── agt/
│       └── house_agent.asl
├── java/
│   └── example/
│       └── SmartHomeEnv.java
├── build.gradle
├── .gitignore
├── LICENSE
└── README.md
```

If `house_agent.asl` is in a different folder, verify that the path in `smart_home_project.mas2j` matches:

```
aslSourcePath: "src/agt";
```

---

## 4. Build the Project

This downloads JaCaMo, compiles Java code, and prepares the classpath:

```bash
# macOS / Linux
./gradlew build

# Windows
gradlew.bat build
```

**Expected output:**
```
BUILD SUCCESSFUL in 15s
```

> The first build may take 1–3 minutes as Gradle downloads JaCaMo from the Maven repository.

---

## 5. Run the Simulation

```bash
# macOS / Linux
./gradlew run

# Windows
gradlew.bat run
```

---

## 6. What You Will See

### Java Swing Window
A 5×5 grid opens showing:
- **Red circle labelled "1"** = Agent 1
- **Blue circle labelled "2"** = Agent 2
- **"Table", "Chair", "Door"** = Target tasks (turn **green** when completed)
- **"Brush", "Key", "Code", "Color"** = Tools on the grid (in blue text)
- **Dark grey cells** = Obstacles / walls

### Console Output (during simulation)
```
--- SYSTEM START ---
>>> STARTING EPISODE: 1 (System Reset ID: 1)
[agent1] >>> DETECTED INITIAL EPISODE 1 <<<
[agent2] >>> DETECTED INITIAL EPISODE 1 <<<
[agent1] I WIN table
[agent2] I WIN door
[agent1] Optimized Path: Table is closer. Doing Table -> Chair.
[agent1] Got brush
[agent1] Got color
[agent1] FINISHED table
[agent1] FINISHED chair
[agent2] Got key
[agent2] Got code
[agent2] FINISHED DOOR.
   -> Ep 1 Finished. Utility: 2.41 | Steps: 19
>>> STARTING EPISODE: 2 (System Reset ID: 2)
...
```

### Final Statistics (after 100 episodes)
```
------------------------------------------------
DONE 100 EPISODES.
FINAL AVERAGE UTILITY: 2.1847
TOTAL ACTIONS TAKEN: 1923
------------------------------------------------
```

---

## 7. Common Errors and Fixes

### Error: `Could not find or load main class jacamo.infra.JaCaMoLauncher`
**Cause:** JaCaMo JAR not on classpath.
**Fix:** Run `./gradlew build` first. If it persists, delete `.gradle/` and rebuild:
```bash
rm -rf .gradle/
./gradlew build
./gradlew run
```

---

### Error: `FileNotFoundException: house_agent.asl`
**Cause:** The `.asl` file is not in `src/agt/` but the `mas2j` declares `aslSourcePath: "src/agt"`.
**Fix:** Move your `.asl` file to the correct path:
```bash
mkdir -p src/agt
mv house_agent.asl src/agt/
```

---

### Error: `Cannot find symbol: example.SmartHomeEnv`
**Cause:** The Java file isn't being compiled because `build.gradle` doesn't know where to find it.
**Fix:** Verify `build.gradle` includes:
```groovy
sourceSets {
    main {
        java { srcDirs = ['java'] }
    }
}
```
And that your file is at `java/example/SmartHomeEnv.java`.

---

### Error: Java Swing window doesn't open (headless environment)
**Cause:** Running on a server or CI without a display.
**Fix:** This project requires a graphical display. Run it on a local machine, not a remote server. If on a remote Linux machine, use X11 forwarding:
```bash
ssh -X user@server
./gradlew run
```

---

### Warning: `!!! DEADLOCK (High Collision Rate)! RETRYING EPISODE X !!!`
**This is expected behaviour.** The deadlock detection system automatically resets the episode and retries. This is not an error — it is a designed safety mechanism.

---

## 8. Adjusting Parameters

All key parameters are at the top of `SmartHomeEnv.java`:

```java
public static final int GSize = 5;       // Grid size (NxN)
private final int MAX_EPISODES = 100;     // Number of episodes to run
private int stepsWithoutScore = 0;        // Deadlock threshold (line ~188: > 30)
private int consecutiveMoveFailures = 0;  // Move failure threshold (line ~183: > 15)
```

And in `executeAction`:
```java
double stepCost = 0.01;    // Cost per step
stepCost += (itemsCarried * 0.02);  // Extra cost per item
if (model.hasMixedItems(agId)) stepCost += 0.03;  // Mixed item penalty
```

---

## 9. Running Without the GUI (Headless Mode)

To suppress the visual window (useful for benchmarking or running remotely), you can add this JVM argument to `build.gradle`:

```groovy
jvmArgs = ['-Djava.awt.headless=true']
```

Note: This will suppress the grid view but still print all console output.
