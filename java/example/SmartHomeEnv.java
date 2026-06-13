package example;

import jason.asSyntax.*;
import jason.environment.Environment;
import jason.environment.grid.GridWorldModel;
import jason.environment.grid.GridWorldView;
import jason.environment.grid.Location;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.*;

/**
 * Main Environment Class.
 * This class is the bridge between the agents (.asl code) and the GridWorld.
 * MAIN FUNCTIONS:
 * 1. Maintains the state of the world (objects, positions, walls)
 * 2. Executes actions requested by agents (move, pick, paint)
 * 3. Computes the score (utility) and action costs.
 * 4. Manages the flow of episodes (reset after success or deadlock)
 */
public class SmartHomeEnv extends Environment {


    public static final int GSize = 5;
    public static final int BRUSH = 8;
    public static final int KEY   = 16;
    public static final int CODE  = 32;
    public static final int COLOR = 64;
    public static final int TABLE = 128;
    public static final int CHAIR = 256;
    public static final int DOOR  = 512;
    public static final int OBSTACLE = GridWorldModel.OBSTACLE;


    private int statsEpisodeCount = 0;

    /* * perceptId: A unique ID that increments each time the world resets.
     * Lets agents know that the world changed so they can discard their old memory/beliefs.
     */
    private int perceptId = 0;
    private final int MAX_EPISODES = 100;

    private boolean simulationEnded = false;

    // UTILITY VARIABLES
    private double currentUtility = 0.0;          // Points in the current episode
    private double totalAccumulatedUtility = 0.0; // Cumulative points across all episodes (for average)
    private boolean episodeFinished = false;      // True when all 3 targets are completed (Table, Chair, Door)

    //  DEADLOCK & STEP VARIABLES
    private int timeSteps = 0;               // Steps taken in the current episode
    private int totalAccumulatedSteps = 0;   // Total steps across the entire experiment

    // Safety mechanism: If 30 steps pass without scoring, assume they are stuck
    private int stepsWithoutScore = 0;

    // Safety mechanism: If movement (move) fails many times in a row, trigger reset
    private int consecutiveMoveFailures = 0;

    private HomeModel model; // The logical world model
    private HomeView  view;  // The graphical grid view

    // Environment initialization
    @Override
    public void init(String[] args) {
        System.out.println("--- SYSTEM START ---");
        statsEpisodeCount = 0;
        perceptId = 0;
        totalAccumulatedUtility = 0.0;
        totalAccumulatedSteps = 0;
        simulationEnded = false;

        startNewEpisode();
    }


    // Called at the start and each time a target finishes or there is a deadlock
    private void startNewEpisode() {
        episodeFinished = false;
        currentUtility = 0.0;
        timeSteps = 0;
        stepsWithoutScore = 0;
        consecutiveMoveFailures = 0;

        perceptId++; // New ID to notify agents

        if (view != null) view.setVisible(false);

        // Create new model
        model = new HomeModel();
        view = new HomeView(model);
        model.setView(view);

        model.resetEpisode(statsEpisodeCount);

        // Update agent percepts
        updatePercepts();
        informAgsEnvironmentChanged(); // Notifies Jason to read the new data
        if (view != null) view.repaint();

        System.out.println(">>> STARTING EPISODE: " + (statsEpisodeCount + 1) + " (System Reset ID: " + perceptId + ")");
    }


    // Helper method for restart when agents get stuck
    private void retryCurrentEpisode(String reason) {
        System.out.println("!!! DEADLOCK (" + reason + ")! RETRYING EPISODE " + (statsEpisodeCount + 1) + " !!!");
        // does not increment statsEpisodeCount, so they retry the same level.
        startNewEpisode();
    }

    /**
     * executeAction: The main method called when agents request to execute an action.
     * Commands arrive here from the .asl files.
     */
    @Override
    public boolean executeAction(String ag, Structure action) {
        // If the experiment has ended, ignore any commands
        if (episodeFinished || simulationEnded) return true;

        int agId = (ag.contains("2")) ? 1 : 0;

        timeSteps++;
        stepsWithoutScore++; // Increment the counter of steps without scoring

        // ACTION COST CALCULATION
        double stepCost = 0.01; // Base cost for each move

        // Weight penalty: The more items carried, the more expensive the move
        int itemsCarried = model.countCarriedItems(agId);
        stepCost += (itemsCarried * 0.02);

        // Incompatible item penalty: If carrying painting tools and keys together
        if (model.hasMixedItems(agId)) stepCost += 0.03;

        boolean result = false;

        try {
            // COMMAND HANDLING

            // 1. A* movement (move_astar(X, Y))
            if (action.getFunctor().equals("move_astar")) {
                int targetX = (int)((NumberTerm)action.getTerm(0)).solve();
                int targetY = (int)((NumberTerm)action.getTerm(1)).solve();
                result = model.moveAStar(agId, targetX, targetY);

                // Count failed movements
                if (!result) consecutiveMoveFailures++;
                else consecutiveMoveFailures = 0;
            }
            else if (action.getFunctor().equals("pick")) {
                String item = action.getTerm(0).toString();
                result = model.pickItem(agId, item);
            }
            else if (action.getFunctor().equals("drop")) {
                String item = action.getTerm(0).toString();
                result = model.dropItem(agId, item);
            }
            else if (action.getFunctor().equals("paint")) {
                String obj = action.getTerm(0).toString();
                result = model.paintObject(agId, obj);
                if(result) {
                    currentUtility += 1.0;
                    stepsWithoutScore = 0; // Reset the steps-without-scoring counter
                }
            }
            else if (action.getFunctor().equals("open_door")) {
                result = model.openDoor(agId);
                if(result) {
                    currentUtility += 0.8;
                    stepsWithoutScore = 0;
                }
            }
        } catch (Exception e) { e.printStackTrace(); }

        // Deduct cost from score (if the action completed technically)
        if (result) currentUtility -= stepCost;

        // DEADLOCK CHECK
        // Case 1: Many consecutive failed moves
        if (consecutiveMoveFailures > 15) {
            retryCurrentEpisode("High Collision Rate");
            return true;
        }
        // Case 2: Too much time passes without scoring
        if (stepsWithoutScore > 30) {
            retryCurrentEpisode("Stalling / No Progress");
            return true;
        }

        // Update state and agents
        updatePercepts();
        try { Thread.sleep(50); } catch (Exception e) {} // Delay to observe movement visually
        informAgsEnvironmentChanged();
        if(view != null) view.repaint();

        // EPISODE TERMINATION CHECK
        // If all tasks (Table, Chair, Door) are completed
        if (model.tablePainted && model.chairPainted && model.doorOpened) {
            episodeFinished = true;
            handleEpisodeEnd();
        }
        return result;
    }

    /**
     * Episode End Management.
     * Saves statistics and decides whether to start a new episode or end the experiment.
     */
    private void handleEpisodeEnd() {
        statsEpisodeCount++;
        totalAccumulatedUtility += currentUtility;
        totalAccumulatedSteps += timeSteps;

        System.out.println(String.format(java.util.Locale.US, "   -> Ep %d Finished. Utility: %.2f | Steps: %d", statsEpisodeCount, currentUtility, timeSteps));

        // If not 100 episodes yet, start next
        if (statsEpisodeCount < MAX_EPISODES) {
            startNewEpisode();
        } else {
            // END OF ENTIRE EXPERIMENT
            double average = totalAccumulatedUtility / (double)MAX_EPISODES;

            System.out.println("------------------------------------------------");
            System.out.println("DONE " + MAX_EPISODES + " EPISODES.");
            System.out.println(String.format(java.util.Locale.US, "FINAL AVERAGE UTILITY: %.4f", average));
            System.out.println("TOTAL ACTIONS TAKEN: " + totalAccumulatedSteps);
            System.out.println("------------------------------------------------");

            // Activate termination flag and notify agents
            simulationEnded = true;
            updatePercepts();
            informAgsEnvironmentChanged();
        }
    }


    void updatePercepts() {
        updateAgPercepts("agent1", 0);
        updateAgPercepts("agent2", 1);
    }

    /**
     * Create percepts for a specific agent.
     */
    void updateAgPercepts(String agName, int agId) {
        clearPercepts(agName); // Clear old percepts

        // If simulation ended, send only this message
        if (simulationEnded) {
            addPercept(agName, Literal.parseLiteral("simulation_finished"));
            return;
        }

        addPercept(agName, Literal.parseLiteral("my_id(" + (agId+1) + ")")); // Who
        Location l = model.getAgPos(agId);
        addPercept(agName, Literal.parseLiteral("pos(" + l.x + "," + l.y + ")")); // Where

        // TARGET LOCATIONS
        Location tLoc = model.targetLocs.get("table");
        Location cLoc = model.targetLocs.get("chair");
        Location dLoc = model.targetLocs.get("door");
        addPercept(agName, Literal.parseLiteral("loc(table," + tLoc.x + "," + tLoc.y + ")"));
        addPercept(agName, Literal.parseLiteral("loc(chair," + cLoc.x + "," + cLoc.y + ")"));
        addPercept(agName, Literal.parseLiteral("loc(door," + dLoc.x + "," + dLoc.y + ")"));

        // OBJECT DETECTION (Scan Grid)
        Location brushLoc = model.findObj(BRUSH);
        Location keyLoc   = model.findObj(KEY);
        Location codeLoc  = model.findObj(CODE);
        Location colorLoc = model.findObj(COLOR);

        // Add percept only if the object exists on the grid (not held by an agent)
        if (brushLoc != null) addPercept(agName, Literal.parseLiteral("loc(brush," + brushLoc.x + "," + brushLoc.y + ")"));
        if (keyLoc   != null) addPercept(agName, Literal.parseLiteral("loc(key,"   + keyLoc.x   + "," + keyLoc.y   + ")"));
        if (codeLoc  != null) addPercept(agName, Literal.parseLiteral("loc(code,"  + codeLoc.x  + "," + codeLoc.y  + ")"));
        if (colorLoc != null) addPercept(agName, Literal.parseLiteral("loc(color," + colorLoc.x + "," + colorLoc.y + ")"));

        // INVENTORY
        if (model.agHasBrush[agId]) addPercept(agName, Literal.parseLiteral("carrying(brush)"));
        if (model.agHasKey[agId])   addPercept(agName, Literal.parseLiteral("carrying(key)"));
        if (model.agHasCode[agId])  addPercept(agName, Literal.parseLiteral("carrying(code)"));
        if (model.agHasColor[agId]) addPercept(agName, Literal.parseLiteral("carrying(color)"));

        // TARGET STATUS
        if (model.tablePainted) addPercept(agName, Literal.parseLiteral("completed(table)"));
        if (model.chairPainted) addPercept(agName, Literal.parseLiteral("completed(chair)"));
        if (model.doorOpened)   addPercept(agName, Literal.parseLiteral("completed(door)"));

        // Episode ID for reset
        addPercept(agName, Literal.parseLiteral("episode_id(" + perceptId + ")"));

        if (statsEpisodeCount < MAX_EPISODES && !episodeFinished) {
             addPercept(agName, Literal.parseLiteral("episode_active"));
        }
    }

    /**
     * Create percepts for all agents.
     */
    void updateAllAgents(int epNum, boolean finishedEpisode, boolean simulationEndedFlag) {
         updateAgPercepts("agent1", 0);
         updateAgPercepts("agent2", 1);
    }

    class HomeModel extends GridWorldModel {

        boolean[] agHasBrush = {false, false};
        boolean[] agHasKey   = {false, false};
        boolean[] agHasCode  = {false, false};
        boolean[] agHasColor = {false, false};

        // Target completion status
        boolean tablePainted = false, chairPainted = false, doorOpened = false;
        Map<String, Location> targetLocs = new HashMap<>();

        private HomeModel() {
            super(GSize, GSize, 2);
            add(OBSTACLE, 1, 3); add(OBSTACLE, 1, 4);
            add(OBSTACLE, 3, 1); add(OBSTACLE, 3, 0);
            add(BRUSH, 0, 0); add(KEY, 0, 1);
            add(CODE, 2, 0); add(COLOR, 4, 0);
        }

        // Scans the grid to find an object by bitmask
        public Location findObj(int mask) {
            for (int x = 0; x < getWidth(); x++) {
                for (int y = 0; y < getHeight(); y++) {
                    if ((data[x][y] & mask) != 0) return new Location(x, y);
                }
            }
            return null;
        }


        // Resets the grid for a new episode.

        void resetEpisode(int epNum) {
            // Reset states
            tablePainted = false; chairPainted = false; doorOpened = false;
            Arrays.fill(agHasBrush, false); Arrays.fill(agHasKey, false);
            Arrays.fill(agHasCode, false); Arrays.fill(agHasColor, false);

            // Place agents at initial positions
            try { setAgPos(0, 0, 4); } catch (Exception e) {}
            try { setAgPos(1, 2, 2); } catch (Exception e) {}

            // Clear old targets (Table/Chair/Door) from the grid
            for(int x=0; x<GSize; x++) {
                for(int y=0; y<GSize; y++) {
                    remove(TABLE, x, y); remove(CHAIR, x, y); remove(DOOR, x, y);
                }
            }

            // Episode 0 uses fixed positions
            // Subsequent episodes use random positions
            if (epNum == 0) {
                addTarget("table", TABLE, 4, 4);
                addTarget("chair", CHAIR, 3, 3);
                addTarget("door", DOOR, 2, 4);
            } else {
                placeRandomTarget("table", TABLE);
                placeRandomTarget("chair", CHAIR);
                placeRandomTarget("door", DOOR);
            }
        }

        // Places a target at a random position, avoiding obstacles and other targets
        private void placeRandomTarget(String name, int mask) {
            Random rand = new Random();
            int x, y;
            boolean valid;
            do {
                x = rand.nextInt(GSize); y = rand.nextInt(GSize); valid = true;
                // Forbidden positions (agent starting positions)
                if (x == 0 && y == 4) valid = false;
                if (x == 2 && y == 2) valid = false;
                // Check if something already exists there
                if (hasObject(OBSTACLE, x, y)) valid = false;
                if (hasObject(KEY, x, y) || hasObject(BRUSH, x, y) || hasObject(CODE, x, y) || hasObject(COLOR, x, y)) valid = false;
                for (Location loc : targetLocs.values()) { if (loc.x == x && loc.y == y) valid = false; }
            } while (!valid);
            addTarget(name, mask, x, y);
        }

        private void addTarget(String name, int mask, int x, int y) {
            add(mask, x, y);
            targetLocs.put(name, new Location(x, y));
        }


        // A* movement implementation — computes the path for the agent to reach targetX, targetY

        boolean moveAStar(int agId, int targetX, int targetY) {
            Location start = getAgPos(agId);
            if (start == null) return false; // If agent is not on the grid, movement fails

            Location target = new Location(targetX, targetY);
            if (start.equals(target)) return true; // Already there

            // Find where the other agent is to avoid it
            int otherAgId = (agId == 0) ? 1 : 0;
            Location otherAgPos = getAgPos(otherAgId);

            // Attempt 1: path avoiding the other agent
            List<Location> path = findPathAStar(start, target, otherAgPos, true);

            // Attempt 2: If no path exists, ignore the other agent
            // (The other may move in the next step)
            if (path == null || path.isEmpty()) path = findPathAStar(start, target, otherAgPos, false);

            if (path != null && !path.isEmpty()) {
                Location nextStep = path.get(0);
                // Final collision check before moving
                if (nextStep.equals(otherAgPos)) return false;
                setAgPos(agId, nextStep);
                return true;
            }
            return false;
        }


        // The A* algorithm for finding the optimal path
        private List<Location> findPathAStar(Location start, Location target, Location otherAgPos, boolean avoidOther) {
            PriorityQueue<Node> openList = new PriorityQueue<>();
            boolean[][] closedList = new boolean[getWidth()][getHeight()];

            // Initialization
            Node startNode = new Node(start.x, start.y, null);
            startNode.g = 0;
            startNode.h = Math.abs(start.x - target.x) + Math.abs(start.y - target.y); // Manhattan distance
            startNode.f = startNode.g + startNode.h;
            openList.add(startNode);

            while (!openList.isEmpty()) {
                Node current = openList.poll(); // Get the node with the smallest f (cost)

                // If target reached, reconstruct the path backwards
                if (current.x == target.x && current.y == target.y) {
                    List<Location> path = new ArrayList<>();
                    while (current.parent != null) { path.add(0, new Location(current.x, current.y)); current = current.parent; }
                    return path;
                }
                closedList[current.x][current.y] = true;

                // Check neighbors
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = current.x + d[0]; int ny = current.y + d[1];
                    // Validity check (within grid, not a wall, not in closed list)
                    if (inGrid(nx, ny) && !closedList[nx][ny] && (data[nx][ny] & OBSTACLE) == 0) {
                        // If the other agent should be avoided
                        if (avoidOther && nx == otherAgPos.x && ny == otherAgPos.y) continue;

                        int newG = current.g + 1;
                        int newH = Math.abs(nx - target.x) + Math.abs(ny - target.y);
                        Node neighbor = new Node(nx, ny, current);
                        neighbor.g = newG; neighbor.h = newH; neighbor.f = newG + newH;
                        openList.add(neighbor);
                    }
                }
            }
            return null; // No path found
        }

        class Node implements Comparable<Node> {
            int x, y, g, h, f; Node parent;
            public Node(int x, int y, Node p) { this.x = x; this.y = y; this.parent = p; }
            public int compareTo(Node other) { return Integer.compare(this.f, other.f); }
        }

        boolean pickItem(int agId, String item) {
            Location loc = getAgPos(agId);
            int mask = 0;
            if(item.equals("brush")) mask = BRUSH; if(item.equals("key")) mask = KEY;
            if(item.equals("code")) mask = CODE; if(item.equals("color")) mask = COLOR;

            // Capacity check (max 3 items)
            if (countCarriedItems(agId) >= 3) return false;

            // If the object exists in the cell
            if (hasObject(mask, loc)) {
                remove(mask, loc); // Remove from grid
                // Update inventory
                if(item.equals("brush")) agHasBrush[agId]=true;
                if(item.equals("key")) agHasKey[agId]=true;
                if(item.equals("code")) agHasCode[agId]=true;
                if(item.equals("color")) agHasColor[agId]=true;
                return true;
            }
            return false;
        }

        boolean dropItem(int agId, String item) {
             Location loc = getAgPos(agId);
             int mask = 0; boolean dropped = false;
             if(item.equals("brush") && agHasBrush[agId]) { mask = BRUSH; agHasBrush[agId]=false; dropped=true;}
             if(item.equals("key")   && agHasKey[agId])   { mask = KEY;   agHasKey[agId]=false;   dropped=true;}
             if(item.equals("code")  && agHasCode[agId])  { mask = CODE;  agHasCode[agId]=false;  dropped=true;}
             if(item.equals("color") && agHasColor[agId]) { mask = COLOR; agHasColor[agId]=false; dropped=true;}

             if(dropped) { add(mask, loc); return true; } // Add back to grid
             return false;
        }

        // PAINTING
        boolean paintObject(int agId, String obj) {
            Location loc = getAgPos(agId);
            // Prerequisite: Must have brush and color
            if (agHasBrush[agId] && agHasColor[agId]) {
                if (obj.equals("table") && hasObject(TABLE, loc) && !tablePainted) {
                    tablePainted = true; return true;
                }
                if (obj.equals("chair") && hasObject(CHAIR, loc) && !chairPainted) {
                    chairPainted = true; return true;
                }
            }
            return false;
        }

        // DOOR
        boolean openDoor(int agId) {
            Location loc = getAgPos(agId);
            // Prerequisite: Must have key and code
            if (agHasKey[agId] && agHasCode[agId] && hasObject(DOOR, loc) && !doorOpened) {
                doorOpened = true; return true;
            }
            return false;
        }

        int countCarriedItems(int agId) {
            int c=0; if(agHasBrush[agId])c++; if(agHasKey[agId])c++; if(agHasCode[agId])c++; if(agHasColor[agId])c++; return c;
        }

        // Checks if the agent is carrying incompatible tools
        boolean hasMixedItems(int agId) {
            boolean hasPaint = agHasBrush[agId] || agHasColor[agId];
            boolean hasDoor = agHasKey[agId] || agHasCode[agId];
            return hasPaint && hasDoor;
        }
    }

    // The Graphical View Class
    class HomeView extends GridWorldView {
        public HomeView(HomeModel model) {
            super(model, "Smart Home", 600);
            defaultFont = new Font("Arial", Font.BOLD, 12);
            setVisible(true);
            repaint();
        }

        @Override
        public void draw(Graphics g, int x, int y, int object) {
            g.setColor(Color.WHITE); g.fillRect(x * cellSizeW, y * cellSizeH, cellSizeW, cellSizeH);
            g.setColor(Color.LIGHT_GRAY); g.drawRect(x * cellSizeW, y * cellSizeH, cellSizeW, cellSizeH);

            if ((object & OBSTACLE) != 0) {
                g.setColor(Color.DARK_GRAY); g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 1, cellSizeH - 1);
            }

            HomeModel hm = (HomeModel) model;
            boolean isDone = false;
            if ((object & TABLE) != 0 && hm.tablePainted) isDone = true;
            if ((object & CHAIR) != 0 && hm.chairPainted) isDone = true;
            if ((object & DOOR)  != 0 && hm.doorOpened)   isDone = true;

            if (isDone) {
                g.setColor(new Color(200, 255, 200)); // Light green
                g.fillRect(x * cellSizeW + 1, y * cellSizeH + 1, cellSizeW - 1, cellSizeH - 1);
            }

            g.setColor(Color.BLACK);
            if ((object & TABLE) != 0) drawString(g, x, y, defaultFont, "Table");
            if ((object & CHAIR) != 0) drawString(g, x, y, defaultFont, "Chair");
            if ((object & DOOR) != 0)  drawString(g, x, y, defaultFont, "Door");

            g.setColor(Color.BLUE);
            if ((object & BRUSH) != 0) drawString(g, x, y, defaultFont, "Brush");
            if ((object & KEY)   != 0) drawString(g, x, y, defaultFont, "Key");
            if ((object & CODE)  != 0) drawString(g, x, y, defaultFont, "Code");
            if ((object & COLOR) != 0) drawString(g, x, y, defaultFont, "Color");

            Location p1 = hm.getAgPos(0);
            Location p2 = hm.getAgPos(1);

            if(p1 != null && p1.x == x && p1.y == y) {
                g.setColor(Color.RED);
                g.fillOval(x * cellSizeW + 10, y * cellSizeH + 10, cellSizeW - 20, cellSizeH - 20);
                g.setColor(Color.WHITE); drawString(g, x, y, defaultFont, "1");
            }

            if(p2 != null && p2.x == x && p2.y == y) {
                g.setColor(Color.BLUE);
                g.fillOval(x * cellSizeW + 10, y * cellSizeH + 10, cellSizeW - 20, cellSizeH - 20);
                g.setColor(Color.WHITE); drawString(g, x, y, defaultFont, "2");
            }
        }
    }
}