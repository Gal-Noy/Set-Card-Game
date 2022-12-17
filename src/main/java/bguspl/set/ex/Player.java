package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;

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
     * Player's chosen slots.
     */
    private final Queue<Integer> chosenSlots;

    /**
     * chosenSlots queue lock.
     */
    private final ReadWriteLock chosenSlotsLock;

    /**
     * The time when the player freeze will time out.
     */
    private volatile long freezeTime = -1;

    /**
     * signifier if the player's set is being examined by the dealer.
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
        this.chosenSlotsLock = new ReentrantReadWriteLock();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {


            // Sleep until woken by input manager thread or game termination.
            synchronized (this) {
                while (queueSize() == 0 && !terminate)
                    try {
                        wait();
                    } catch (InterruptedException ignored) {
                    }
            }

            int clickedSlot = -1;

            try{
                chosenSlotsLock.writeLock().lock();
                if (!chosenSlots.isEmpty()) clickedSlot = chosenSlots.remove();
            } finally {
                chosenSlotsLock.writeLock().unlock();
            }

            // Allow actions iff game is running and table is available.
            if (clickedSlot != -1 && table.tableReady && !terminate) {
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
        } catch (
                InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().

                getName() + " terminated.");
    }

    /**
     * check if the chosenSlots queue is empty.
     * @return - true iff the chosenSlots queue is empty.
     */
    private int queueSize() {
        int output;
        try {
            chosenSlotsLock.readLock().lock();
            output = chosenSlots.size();
        } finally {
            chosenSlotsLock.readLock().unlock();
        }
        return output;
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

                // Pick random slot from the table.
                if (queueSize() < env.config.featureSize)
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
        try {
            chosenSlotsLock.writeLock().lock();

            // Allow key presses iff all conditions are met.
            if (!examined && table.tableReady && freezeTime < System.currentTimeMillis() && chosenSlots.size() < env.config.featureSize) {
                chosenSlots.add(slot);
                notifyAll();
            }
        } finally {
            chosenSlotsLock.writeLock().unlock();
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
        clearChosenSlots();
        env.ui.setScore(id, ++score);

        notifyAll();

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
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
        clearChosenSlots();

        notifyAll();
    }

    /**
     * Clear the chosen slots queue.
     * @post - the chosen slots queue is empty.
     */
    public void clearChosenSlots() {
        try {
            chosenSlotsLock.writeLock().lock();
            chosenSlots.clear();
        } finally {
            chosenSlotsLock.writeLock().unlock();
        }
    }

    /**
     * @return - the player's score.
     */
    public int getScore() {
        return score;
    }

    /**
     * @post - playerThread == pThread
     */
    public void setThread(Thread pThread) {
        playerThread = pThread;
    }

    /**
     * @return - the player's thread.
     */
    public Thread getThread() {
        return playerThread;
    }

    /**
     * @return - player's id
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
     * @return - player's freezeTime.
     */
    public long getFreezeTime() {
        return freezeTime;
    }

    /**
     * @return - terminate state (for testing)
     */
    public boolean getTerminate() {
        return terminate;
    }

    /**
     * @return - chosenSlots (for testing)
     */
    public Queue<Integer> getChosenSlots() {
        return chosenSlots;
    }
}