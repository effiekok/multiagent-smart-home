# 🏠 Multi-Agent Smart Home System

> A multi-agent AI simulation where two autonomous BDI agents cooperate to complete household tasks inside a grid-world environment — featuring negotiation, A\* pathfinding, deadlock recovery, and a 100-episode learning experiment.

---

## 📖 Description

This project implements a **Multi-Agent System (MAS)** using the **Jason/JaCaMo framework**, where two intelligent agents collaborate to complete a set of tasks in a simulated smart home environment.

Each agent reasons about its environment using **Belief-Desire-Intention (BDI)** logic. The agents must **negotiate task ownership**, **avoid collisions**, **collect required tools**, and **complete three target tasks** (painting a table, painting a chair, and unlocking a door) as efficiently as possible.

The system runs **100 episodes** with randomised target placement, collecting utility and step statistics to measure overall agent performance. The project explores core concepts in **Artificial Intelligence**, **Multi-Agent Systems**, and **Autonomous Reasoning**.

---

## ✨ Features

- 🤖 **Two cooperative BDI agents** sharing a single agent logic file, differentiated at runtime by their ID
- 🗺️ **A\* pathfinding** with real-time collision avoidance between agents
- 🤝 **Negotiation protocol** — agents bid for tasks using distance-based utility scores
- ⚔️ **Conflict resolution** — special rules prevent both agents from competing for painting tools simultaneously
- 🔄 **Dynamic path optimisation** — after collecting tools, agents recalculate the most efficient painting order
- 🔁 **100-episode experiment loop** with randomised environments from episode 2 onward
- ☠️ **Deadlock detection & automatic recovery** — the system detects stuck agents and resets the episode
- 📊 **Utility scoring system** with step costs, weight penalties, and mixed-item penalties
- 🎨 **Live visual grid** rendered in Java Swing, showing agent positions, task states, and tool locations

---

## 🛠️ Technologies Used

### Jason / JaCaMo
**What it is:** Jason is an interpreter for AgentSpeak(L), a BDI agent programming language. JaCaMo is a platform for programming Multi-Agent Systems that integrates Jason (agents), CArtAgO (environment artefacts), and Moise (organisation).

**Why it was used:** This project requires agents with autonomous reasoning — beliefs about the world, desires (goals), and intentions (active plans). Jason's AgentSpeak syntax is purpose-built for this. The `.asl` file directly encodes BDI logic with plan triggering, belief revision, and goal delegation.

**Why it is useful here:** It allows each agent to reason declaratively. Instead of hardcoded if-then logic, the agent's behaviour emerges from its current beliefs and a library of applicable plans — making the system far more robust and extensible.

---

### Java (JDK 8+)
**What it is:** A general-purpose, object-oriented programming language.

**Why it was used:** JaCaMo's environment layer is written in Java. The `SmartHomeEnv.java` class extends Jason's `Environment` base class to define what actions agents can execute (move, pick, drop, paint, open_door) and what percepts they receive.

**Why it is useful here:** Java gives full control over the grid model, action execution, scoring, and the graphical view. The `GridWorldModel` base class (provided by Jason) handles the cell-based grid, and Java's `PriorityQueue` is used directly for A\*.

---

### AgentSpeak(L) — `.asl` files
**What it is:** A logic-based agent programming language built on Prolog-style syntax. Plans are written as: `triggering_event : context <- body.`

**Why it was used:** The entire agent strategy — negotiation, movement, tool collection, failure handling — is expressed in `.asl`. This cleanly separates agent *intelligence* from the Java *environment*.

**Why it is useful here:** Changes to agent strategy require only editing the `.asl` file, with zero Java changes. The language natively supports belief updates (`-+belief`), goal delegation (`!subgoal`), and failure recovery (`-!plan`).

---

### A\* Search Algorithm
**What it is:** An informed graph search algorithm that finds the optimal path between two nodes using the formula `f(n) = g(n) + h(n)` where `g` is the cost so far and `h` is the heuristic (Manhattan distance here).

**Why it was used:** Agents must navigate a 5×5 grid with static obstacles and a dynamic obstacle (the other agent). A\* guarantees the shortest path while allowing the collision avoidance constraint to be toggled.

**Why it is useful here:** A simple BFS would find a path but not necessarily the shortest one. A\* with Manhattan heuristic is optimal for grid navigation. The two-attempt strategy (first avoid the other agent, then ignore if no path exists) prevents permanent deadlock from agent-as-obstacle.

---

### Java Swing (GridWorldView)
**What it is:** Java's built-in GUI toolkit, used here through Jason's `GridWorldView` base class.

**Why it was used:** To render a live visual representation of the grid, agents, tools, and task completion states.

**Why it is useful here:** Makes the simulation observable. Completed tasks are highlighted in green, agents are shown as coloured circles, and tool positions are labelled — making it easy to verify and demonstrate the system's behaviour.

---

### MAS2J Project File
**What it is:** A declarative configuration file (`.mas2j`) used by JaCaMo to wire together the environment class, agent names, and their associated `.asl` files.

**Why it was used:** Required by the JaCaMo framework to bootstrap the multi-agent system.

**Why it is useful here:** Separates configuration from code — adding a third agent requires only editing this file, not the Java or AgentSpeak code.

---

## 📁 Project Structure

```
multi-agent-smart-home/
│
├── smart_home_project.mas2j       # MAS configuration: links agents to environment
│
├── src/
│   └── agt/
│       └── house_agent.asl        # BDI agent logic (shared by both agents)
│
├── java/
│   └── example/
│       └── SmartHomeEnv.java      # Java environment: grid, actions, scoring, A*, view
│
├── screenshots/
│   └── smart_home_architecture_diagram.png
│
├── results/
│   └── sample_output.txt          # Example 100-episode run output
│
├── build.gradle                   # Build configuration (JaCaMo/Gradle)
├── .gitignore
├── LICENSE
└── README.md
```

---

## ⚙️ Prerequisites

Before running this project, ensure you have the following installed:

| Requirement | Version | Notes |
|---|---|---|
| Java JDK | 8 or higher | `java -version` to check |
| Gradle | 7.x or higher | Or use the Gradle Wrapper (`./gradlew`) |
| JaCaMo | 0.9.x | Downloaded automatically via Gradle |

---

## 🚀 Installation & Setup

### Step 1 — Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/multi-agent-smart-home.git
cd multi-agent-smart-home
```

### Step 2 — Verify Java

```bash
java -version
# Expected: java version "1.8.x" or higher
```

### Step 3 — Build the Project

```bash
# On macOS / Linux
./gradlew build

# On Windows
gradlew.bat build
```

Gradle will automatically download JaCaMo and all required dependencies.

---

## ▶️ Running the Project

```bash
# On macOS / Linux
./gradlew run

# On Windows
gradlew.bat run
```

A **Java Swing window** will open showing the 5×5 grid. Watch agents (red = Agent 1, blue = Agent 2) navigate, collect tools, and complete tasks. When all 100 episodes finish, final statistics are printed to the console.

### Expected Console Output

```
--- SYSTEM START ---
>>> STARTING EPISODE: 1 (System Reset ID: 1)
   -> Ep 1 Finished. Utility: 2.34 | Steps: 18
>>> STARTING EPISODE: 2 (System Reset ID: 2)
   ...
------------------------------------------------
DONE 100 EPISODES.
FINAL AVERAGE UTILITY: 2.1847
TOTAL ACTIONS TAKEN: 1923
------------------------------------------------
```

---

## 🧠 How It Works

### Agent Decision Flow

```
START
  │
  ▼
Compute Manhattan distance to all 3 targets
  │
  ▼
Negotiate: broadcast utility score to all agents
  │
  ▼
Resolve: compare bids, apply conflict rules
  │
  ├─ WIN ──► Collect tools ──► Optimise path ──► Execute task
  │
  └─ LOSE ──► Switch to next best task
```

### Negotiation Protocol
Each agent computes a **utility score** for each task:
- If already doing the task: `Util = 1000` (strong commitment signal)
- Otherwise: `Util = 100 - ManhattanDistance` (closer = higher score)

Agents broadcast their offer, wait 500ms, then compare. The **higher utility wins**. Ties go to **Agent 1** (lower ID).

### Special Conflict Rule
If Agent 2 wants to paint a chair and Agent 1 is already negotiating for the table, Agent 2 yields — preventing both agents from racing to pick up the same painting tools (brush + color).

### Utility Scoring
| Action | Score |
|---|---|
| Paint table | +1.0 |
| Paint chair | +1.0 |
| Open door | +0.8 |
| Each step taken | −0.01 |
| Each item carried (per step) | −0.02 |
| Carrying mixed tools (per step) | −0.03 |

---

## 📊 Results

After 100 episodes with randomised target placement:

| Metric | Value |
|---|---|
| Average Utility per Episode | ~2.18 |
| Total Actions Taken | ~1,900 |
| Deadlock Rate | < 5% |
| Episodes Completed Successfully | 100/100 |

> Note: Results are stochastic due to random target placement. Values will vary between runs.

---

## ⚠️ Known Limitations

- The grid is fixed at 5×5. Scaling requires changes to `SmartHomeEnv.java`.
- Both agents share the same `.asl` file — asymmetric strategies would require separate files.
- The visual grid runs at a fixed 50ms delay per step; there is currently no speed control.
- Tool positions (brush, key, code, color) are fixed across all episodes and do not randomise.
- The system does not persist results to disk — statistics are printed to console only.

---

## 📚 References & Credits

- [Jason / AgentSpeak Documentation](http://jason.sourceforge.net/)
- [JaCaMo Multi-Agent Platform](http://jacamo.sourceforge.net/)
- Russell, S. & Norvig, P. — *Artificial Intelligence: A Modern Approach* (A\* algorithm)
- Wooldridge, M. — *An Introduction to MultiAgent Systems* (BDI architecture)
- Hart, P. E., Nilsson, N. J., & Raphael, B. (1968). *A Formal Basis for the Heuristic Determination of Minimum Cost Paths*

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 👤 Author

**Your Name**
- GitHub: [effiekok](https://github.com/YOUR_USERNAME)
- LinkedIn: [Ευθυμία Κοκκίνη](https://www.linkedin.com/in/%CE%B5%CF%85%CE%B8%CF%85%CE%BC%CE%AF%CE%B1-%CE%BA%CE%BF%CE%BA%CE%BA%CE%AF%CE%BD%CE%B7-aa550a401/)
