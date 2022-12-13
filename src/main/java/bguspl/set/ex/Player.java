package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 * @inv freezeTime >= -1
 * @inv 0 <= chosenSlots.size() <= env.config.featureSize
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * Game dealer.
     */
    private final Dealer dealer;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    protected Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    protected final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    protected volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * Player's chosen slots.
     */
    protected final ConcurrentLinkedQueue<Integer> chosenSlots;

    /**
     * The time when the player freeze will time out.
     */
    private volatile long freezeTime = -1;

    /**
     * Signifies that a player chosen set is being examined.
     */
    protected volatile boolean examined = false;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.chosenSlots = new ConcurrentLinkedQueue<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {

            // Sleep until woken by input manager thread or game termination.
            synchronized (this) {
                while (chosenSlots.isEmpty() && !terminate)
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                    }
            }

            // Allow actions iff game is running and table is available.
            if (table.tableReady && !terminate) {
                int clickedSlot = chosenSlots.remove();
                try {
                    table.lockSlot(clickedSlot, false);

                    if (table.slotToCard[clickedSlot] != null)
                        dealer.toggleToken(id, clickedSlot); // Toggle token on slot.
                } finally {
                    table.unlockSlot(clickedSlot, false);
                }
            }
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
//                while (!table.tableReady && !terminate)
//                    try {
//                        Thread.sleep(Long.MAX_VALUE);
//                    } catch (InterruptedException ignored) {
//                    }

                // Pick random slot from the table.
                keyPressed((int) (Math.random() * env.config.tableSize));


            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     *
     * @pre - terminate == false
     * @post - terminate == true
     */
    public synchronized void terminate() {
        notifyAll();
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @pre - none.
     * @post - the key press is added to the queue of key presses.
     */
    public synchronized void keyPressed(int slot) {

        // Allow key presses iff all conditions are met.
        if (!examined && table.tableReady && freezeTime < System.currentTimeMillis() && chosenSlots.size() < env.config.featureSize) {
            chosenSlots.add(slot);
            notifyAll();
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     * @post - the player's freeze time is set to the current time plus the point freeze time.
     * @post - the player's examined is set to false.
     */
    public synchronized void point() {
        freezeTime = Long.sum(System.currentTimeMillis(), env.config.pointFreezeMillis);
        examined = false;
        notifyAll();

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     *
     * @post - the player's freeze time is set to the current time plus the penalty freeze time.
     * @post - the player's examined is set to false.
     */
    public synchronized void penalty() {
        freezeTime = Long.sum(System.currentTimeMillis(), env.config.penaltyFreezeMillis);
        examined = false;
        notifyAll();
    }

    /**
     * Returns the player's score.
     */
    public int getScore() {
        return score;
    }

    /**
     * Sets the player's thread.
     */
    public void setThread(Thread pThread) {
        playerThread = pThread;
    }

    /**
     * Returns the player's thread.
     */
    public Thread getThread() {
        return playerThread;
    }

    /**
     * Returns the player's id.
     */
    public int getId() {
        return id;
    }

    /**
     * @pre - none.
     * @post - freezeTime is set to time.
     */
    public void setFreezeTime(long time) {
        freezeTime = time;
    }

    /**
     * Returns the player's freezeTime.
     */
    public long getFreezeTime() {
        return freezeTime;
    }
}