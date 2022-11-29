package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.SynchronousQueue;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
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
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The player's queue of actions
     */
    private Queue<Integer> pressedKeys;
    /**
     * The player's queue capacity
     */
    private final int queueCapacity = 3;

    /**
     * The class constructor.
     *
     * @param env    - the game environment object.
     * @param table  - the table object.
     * @param dealer - the dealer object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.dealer = dealer;
        this.table = table;
        this.id = id;
        this.human = human;
        this.pressedKeys = new ArrayDeque<>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     * @pre - None.
     * @post - terminate == true
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        // TODO change ai boolean terminate to true
        // TODO terminate player threads
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     * @pre - None.
     * @post - pressedKeys.peek() == slot.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (pressedKeys.size() < queueCapacity)
            pressedKeys.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @pre - None.
     * @post - getScore() == @pre(getScore()) +  1
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     * @pre - None.
     * @post - this.keyPressed(slot);
        * pressedKeys.size() == @pre(pressedKeys.size())
     */
    public void penalty() {
        // TODO implement
    }

    /**
     * @pre - None.
     * @post - None.
     * @return current score.
     */
    public int getScore() {
        return score;
    }

    /**
     * @pre - None.
     * @post - None.
     * @return id.
     */
    public int getId() {
        return id;
    }

    /**
     * @pre - None.
     * @post - None.
     * @return player's queue.
     */
    public Queue<Integer> getQueue() {
        return pressedKeys;
    }
}
