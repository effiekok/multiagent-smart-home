//   SMART HOME AGENT
//   DESCRIPTION: Agent that negotiates tasks, dynamically optimizes its path and collaborates to avoid collisions

!start.

current_ep(0).

// EPISODE MANAGEMENT
+!start : episode_id(ID)
   <- .print(">>> DETECTED INITIAL EPISODE ", ID, " <<<");
      -+current_ep(ID); // Update memory with the current episode ID
      !negotiate_best.  // Start the process of selecting the best task

+!start : not episode_id(_)
   <- .wait(50); !start.

/* * NEW EPISODE DETECTION (RESET):
 * This plan runs automatically when 'episode_id' changes and the new ID is greater than the old one
 */
+episode_id(NewID) : current_ep(OldID) & NewID > OldID
   <- .print(">>> NEW EPISODE ", NewID, " DETECTED. RESETTING... <<<");
      .drop_all_desires;
      -+current_ep(NewID); // Update memory
      !reset_beliefs;      // Clear old data
      !negotiate_best.     // Start fresh for the new episode

/* MEMORY CLEANUP:
 * Clears old offers, completed tasks (task_done) and current activities (doing)
 */
+!reset_beliefs : true
   <- .abolish(offer(_,_,_));
      .abolish(task_done(_));
      .abolish(doing(_)).


/* PLAN: !negotiate_best
 * Logic: The agent looks around, computes Manhattan Distances for ALL targets (Table, Chair, Door) and chooses to negotiate
 * the one closest to it. This happens at the start of each episode and after each completed task,
 * to adapt to environment changes (e.g. if the other agent has moved or has taken a tool)
 */
+!negotiate_best : pos(Mx, My) & loc(table, Tx, Ty) & loc(chair, Cx, Cy) & loc(door, Dx, Dy)
   <- Dt = math.abs(Tx-Mx) + math.abs(Ty-My); // Distance to Table
      Dc = math.abs(Cx-Mx) + math.abs(Cy-My); // Distance to Chair
      Dd = math.abs(Dx-Mx) + math.abs(Dy-My); // Distance to Door

      // Compare distances to find the minimum
      if (Dt <= Dc & Dt <= Dd) { !negotiate(table); // Table is closer
      }
      else { if (Dc < Dt & Dc <= Dd) { !negotiate(chair); // Chair is closer
      }
             else { !negotiate(door); } }. // Door is closer

// NEGOTIATION

// PLAN: !negotiate(Task)
// Role: Computes the Utility
+!negotiate(Task) : loc(Task, Tx, Ty) & pos(Mx, My) & my_id(Me)
   <- // If already doing this task
      if (doing(Task)) { Util = 1000; }
      // Otherwise, score is higher the closer it is
      else { Dist = (math.abs(Tx-Mx) + math.abs(Ty-My)); Util = 100 - Dist; }

      // Broadcasts the offer to all
      .broadcast(tell, offer(Task, Util, Me));
      .wait(500);
      !resolve(Task, Util, Me). // who won


//  CONFLICT RESOLUTION

// PLAN: !resolve
// Checks for potential conflicts between agents when negotiating for painting tasks (table and chair)
// GOAL: Prevent both agents from picking up painting tools at the same time

+!resolve(Task, MyUtil, Me) : true
   <-
      // SPECIAL RULE FOR AGENT 2
      // If it is Agent 2 and wants to paint
      if (Me == 2 & (Task == table | Task == chair)) {
           // Checks what offers Agent 1 made
           .findall(offer(table, _, 1), offer(table, _, 1), TableBids);
           .findall(offer(chair, _, 1), offer(chair, _, 1), ChairBids);

           // Wants chair, but Agent 1 wants table
           if (Task == chair & not .empty(TableBids)) {
               .print("CONFLICT: Agent 1 taking Table. I yield Chair.");
               !lose(chair); // Yields the chair
           } else {
               // Wants table, but Agent 1 wants chair
               if (Task == table & not .empty(ChairBids)) {
                   .print("CONFLICT: Agent 1 taking Chair. I yield Table.");
                   !lose(table); // Yields
               } else {
                   // If no category conflict, does standard score comparison
                   !standard_resolve(Task, MyUtil, Me);
               }
           }
      } else {
          // Agent 1 (or if doing door) always follows the standard plan
          !standard_resolve(Task, MyUtil, Me);
      }.

// STANDARD RESOLUTION
// Collects all offers for the specific Task into a list L

+!standard_resolve(Task, MyUtil, Me) : true
   <- .findall(offer(Task, U, A), offer(Task, U, A), L);
      !decide(Task, MyUtil, Me, L).

// If the list is empty (no one else responded), wins automatically
+!decide(Task, MyUtil, Me, []) : true <- !win(Task).

// DECISION
// Compares its own Utility (MyUtil) with the best in the list (BestU)
+!decide(Task, MyUtil, Me, L) : true
   <- .max(L, offer(Task, BestU, BestAg)); // Find the maximum offer

      if (MyUtil > BestU) { !win(Task); } // If it has a better score, it wins
      else {
          // In case of a tie, the one with the smaller ID wins (Agent 1)
          if (MyUtil == BestU & Me < BestAg) { !win(Task); }
          else { !lose(Task); } // Otherwise loses
      }.

// WIN & LOSS LOGIC
+!win(Task) : true
   <- .print("I WIN ", Task);
      // If won a painting task, runs the "Smart Painting Plan"
      if (Task == table | Task == chair) {
          !prepare_painting(Task);
      } else {
          !execute_task(Task); // Door has its own logic
      }.

+!lose(Task) : true
   <- .print("I LOST ", Task, ". Switching Category.");
      -doing(Task); // Stops working on this
      // If lost painting, tries the door instead
      if (Task == table) { !negotiate(door); }
      else { if (Task == chair) { !negotiate(door); }
             else { !negotiate(table); } }.

// DYNAMIC PAINTING EXECUTION

/* * STEP 1: PREPARATION
 * First picks up tools (Brush, Color) then decides where to go
 */
+!prepare_painting(OriginalTask) : true
   <- +doing(OriginalTask);
      !get_tool(brush);
      !get_tool(color);
      // Now that it has the tools, its position has changed
      // Must recalculate which target is closest now (the other agent may have moved or something in the environment may have changed)
      !optimize_path.

// STEP 2: OPTIMIZATION
// Computes distances from current position to table and chair and decides which painting order is more efficient
+!optimize_path : pos(Mx, My) & loc(table, Tx, Ty) & loc(chair, Cx, Cy)
   <- Dt = math.abs(Tx-Mx) + math.abs(Ty-My);
      Dc = math.abs(Cx-Mx) + math.abs(Cy-My);

      if (Dt <= Dc) {
          .print("Optimized Path: Table is closer. Doing Table -> Chair.");
          !paint_sequence(table, chair); // Order: table first, then chair
      } else {
          .print("Optimized Path: Chair is closer. Doing Chair -> Table.");
          !paint_sequence(chair, table); // Order: chair first, then table
      }.

// STEP 3: PAINTING SEQUENCE EXECUTION
// Paints the first then paints the second without dropping tools in between
+!paint_sequence(First, Second) : true
   <- !do_single_paint(First); // Paint the first

      if (not task_done(Second)) {
          .print(">>> CHAINING: Keeping tools for ", Second, " <<<");
          !do_single_paint(Second); // Paint the second too
      }

      // Now that painting is done, drops the tools
      !drop_tool(brush); !drop_tool(color);

      // Clears memory and looks for what remains
      -doing(table); -doing(chair);
      !find_next_task(First).

// Helper plan for painting a single object (without chaining to the other)
+!do_single_paint(Obj) : true
   <- !at(Obj);        // Move to position
      paint(Obj);      // Execute action in the Environment
      +task_done(Obj); // Mark as done
      .print("FINISHED ", Obj).

// DOOR EXECUTION
+!execute_task(door) : true
   <- +doing(door);
      !get_tool(key); !get_tool(code); // Pick up tools
      !at(door); open_door; +task_done(door); // Open door
      .print("FINISHED DOOR.");
      !drop_tool(key); !drop_tool(code); // Drop tools
      -doing(door);
      !find_next_task(door).

// FIND NEXT TASK
// Checks what has not been done yet and starts negotiation
+!find_next_task(Last) : true
   <- if (not task_done(chair) & Last \== chair & not doing(chair)) { !negotiate(chair); }
      else { if (not task_done(door) & Last \== door & not doing(door)) { !negotiate(door); }
             else { if (not task_done(table) & Last \== table & not doing(table)) { !negotiate(table); }
                    else { .print("DONE."); } } }.

//  TOOLS & MOVEMENT

// If already carrying the tool, does nothing
+!get_tool(Tool) : carrying(Tool) <- true.

// If not carrying it, moves to its location and picks it up
+!get_tool(Tool) : not carrying(Tool) & loc(Tool, Tx, Ty)
   <- !at_location(Tx, Ty); pick(Tool); .print("Got ", Tool).

// If the tool is not on the grid (the other agent has it), waits
+!get_tool(Tool) : not carrying(Tool) & not loc(Tool, _, _)
   <- .print("Waiting for ", Tool, "...");
      .wait(250);
      !get_tool(Tool).

// Error handling during tool acquisition
-!get_tool(Tool) : true <- .wait(250); !get_tool(Tool).

// Drops the tool
+!drop_tool(Tool) : carrying(Tool)
   <- drop(Tool); .print("Dropped ", Tool).
+!drop_tool(Tool) : true.

// Move to an object
+!at(Obj) : loc(Obj, X, Y) <- !at_location(X, Y).
// Move to coordinates
+!at_location(X, Y) : pos(X, Y) <- true. // Already there

// Step-by-step movement with A*
+!at_location(X, Y) : not pos(X, Y)
   <- move_astar(X, Y); .wait(100); !at_location(X, Y).

// If movement fails, waits and retries
-!at_location(X, Y) : true
   <- .print("Blocked. Waiting...");
      .wait(250);
      !at_location(X, Y).

//   FAILURE HANDLING
//   These plans are triggered if an action fails

// 1. Door failure (e.g. already open)
-!execute_task(Task) : true
   <- .print("Action for ", Task, " failed (Already done?). Skipping.");
      -doing(Task);
      if (Task == door) { !drop_tool(key); !drop_tool(code); }
      !find_next_task(Task).

// 2. Painting failure
-!do_single_paint(Obj) : true
   <- .print("Painting ", Obj, " failed (Already done?). Skipping.");
      +task_done(Obj).

// 3. Preparation failure
-!prepare_painting(Task) : true
   <- .print("Failed to prepare for ", Task, ". Resetting.");
      -doing(Task);
      !drop_tool(brush); !drop_tool(color);
      !lose(Task).

// 4. Negotiation failure
-!resolve(Task, _, _) : true
   <- .print("Negotiation glitch for ", Task, ". Assuming Loss...");
      !lose(Task).

// 5. Navigation failure
-!at(Obj) : true.


+simulation_finished
   <- .print(">>> SIMULATION COMPLETED. STOPPING AGENT SYSTEM. <<<");
      .drop_all_desires. // Clear all desires