package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

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
     * Player's chosen slots.
     */
    private Queue<Integer> chosenSlots;

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
        this.chosenSlots = new ArrayDeque<>();
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
            if (chosenSlots.isEmpty()) continue;
            // if can:
            int nextSlot = chosenSlots.remove();
            toggleToken(nextSlot);
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                List<Integer> deck = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
                env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
                    List<Integer> slots = Arrays.stream(set).mapToObj(card -> table.cardToSlot[card]).sorted().collect(Collectors.toList());
                    for (int slot : slots)
                        keyPressed(slot);
                });
            }
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        try {
            if (!human) aiThread.join();
        } catch (InterruptedException ignored) {
            System.out.println(ignored);
        }
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        if (chosenSlots.size() < env.config.featureSize)
            chosenSlots.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
    }

    public int getScore() {
        return score;
    }

    public void setThread(Thread pThread) {
        playerThread = pThread;
    }

    public Thread getThread() {
        return playerThread;
    }

    public int getId() {
        return id;
    }

    public void toggleToken(int slot) {
        Semaphore slotLock = table.getSlotLock(slot);
        try {
            slotLock.acquire();
            Set<Integer> playerSet = dealer.getPlayerSet(id);
            if (playerSet.contains(slot)) {
                playerSet.remove(slot);
                table.removeToken(id, slot);
            } else {
                playerSet.add(slot);
                table.placeToken(id, slot);
            }
            if (playerSet.size() == env.config.featureSize)
                notifyAll(); // Wakes the dealer
        } catch (InterruptedException e) {
            slotLock.release();
        }
    }
}
