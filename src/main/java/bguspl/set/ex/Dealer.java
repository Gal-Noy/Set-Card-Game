package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Enumeration of the modes of game
 */
enum Mode {
    Timer,
    FreePlay,
    Elapsed
}

/**
 * This class manages the dealer's threads and data
 *
 * @inv deck.size() <= env.config.deckSize
 * @inv playersTokens.size() == env.config.HumanPlayers + env.config.ComputerPlayers
 * @inv gameMode == env.config.TurnTimeoutSeconds > 0 ? Mode.Timer : env.config.TurnTimeoutSeconds == 0 ? Mode.Elapsed : Mode.FreePlay
 * @inv setsToCheckByPlayer.size() <= env.config.HumanPlayers + env.config.ComputerPlayers
 * @inv setsToRemove.size() <= number of sets on the table
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The thread representing the dealer.
     */
    private Thread dealerThread;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    protected final List<Integer> deck;

    /**
     * Lock for the deck.
     */
    private final ReadWriteLock deckLock;

    /**
     * True iff game should be terminated due to an external event.
     */
    protected volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * Time stamp to identify timer resets due to reshuffle/set collected.
     */
    protected long elapsedTime;

    /**
     * For each player, hold current pressed slots.
     */
    private final HashMap<Integer, Set<Integer>> playersTokens;

    /**
     * Queue that holds players with a possible set.
     */
    private final Queue<Integer> setsToCheckByPlayer;

    /**
     * Lock for the setsToCheckByPlayer queue.
     */
    private final Lock queueLock;

    /**
     * Queue that holds legal sets to remove from table and deck.
     */
    private final Queue<Integer[]> setsToRemove;

    /**
     * Enumerates current game mode.
     */
    private final Mode gameMode;

    /**
     * final value for one second in milliseconds.
     */
    private final long SECOND = 1000;

    /**
     * final value for ten milliseconds.
     */
    private final long TEN_MILLI_SECONDS = 10;

    /**
     * The class constructor.
     *
     * @param env     - the environment object.
     * @param table   - the table object.
     * @param players - list of the players.
     */
    public Dealer(Env env, Table table, Player[] players) {

        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        this.deckLock = new ReentrantReadWriteLock();

        this.playersTokens = new HashMap<>();
        for (int i = 0; i < this.players.length; i++)
            playersTokens.put(i, new ConcurrentSkipListSet<>());

        this.setsToCheckByPlayer = new ConcurrentLinkedQueue<>();

        this.queueLock = new ReentrantLock(true);

        this.setsToRemove = new ConcurrentLinkedQueue<>();

        this.gameMode = env.config.turnTimeoutMillis > 0 ? Mode.Timer :
                env.config.turnTimeoutMillis < 0 ? Mode.FreePlay : Mode.Elapsed;

        if (gameMode == Mode.Elapsed) this.elapsedTime = System.currentTimeMillis();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

        dealerThread = Thread.currentThread();

        // Start players threads in order of their id's.
        for (Player player : players) {
            player.setThread(new Thread(player, "player " + player.getId()));
            player.getThread().start();
            try {
                Thread.sleep(TEN_MILLI_SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        // Main game loop
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            removeAllCardsFromTable();
        }

        announceWinners();

        terminate();

        // Join players threads in reverse order of their id's.
        for (int i = players.length - 1; i >= 0; i--)
            try {
                players[i].getThread().join();
            } catch (InterruptedException ignored) {
            }

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            examineSets();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {

        // Terminate players in order of their id's.
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
            try {
                Thread.sleep(TEN_MILLI_SECONDS);
            } catch (InterruptedException ignored) {
            }
        }

        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        if (terminate) return true;

        boolean shouldFinish;

        List<Integer> cardsOnTable = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());

        try {
            deckLock.readLock().lock();

            if (!deck.isEmpty())
                shouldFinish = env.util.findSets(deck, 1).size() == 0;
            else
                shouldFinish = env.util.findSets(cardsOnTable, 1).size() == 0;

        } finally {
            deckLock.readLock().unlock();
        }

        return shouldFinish;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    protected void removeCardsFromTable() {
        table.tableReady = false;

        while (!setsToRemove.isEmpty()) {

            Integer[] setToRemove = setsToRemove.remove();

            try {
                // Lock locks.
                table.lockSlots(setToRemove, true);

                for (int slot : setToRemove)
                    table.removeCard(slot);

            } finally {
                // Unlock locks.
                table.unlockSlots(setToRemove, true);
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.tableReady = false;

        // Check if any cards were placed on the table.
        boolean tableChanged = shuffleAndDeal();

        if (gameMode != Mode.Timer) {

            // Check if any sets were found on table.
            List<Integer> cardsOnTable = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
            boolean setsAvailable = env.util.findSets(cardsOnTable, 1).size() > 0;
            reshuffleTime = !setsAvailable ? System.currentTimeMillis() : Long.MAX_VALUE;

        }

        // Reset timer in case table changed.
        if (tableChanged && !shouldFinish()) {
            if (env.config.hints) table.hints();
            updateTimerDisplay(true);
        }

        table.tableReady = true;
    }

    /**
     * Shuffles deck and deals cards to the table.
     *
     * @return - true iff any cards were placed on the table.
     */
    private boolean shuffleAndDeal() {

        Integer[] availableSlots = IntStream.rangeClosed(0, env.config.tableSize - 1).boxed().filter(slot -> table.slotToCard[slot] == null).toArray(Integer[]::new);

        try{
            // Lock locks.
            table.lockSlots(availableSlots, true);
            deckLock.writeLock().lock();

            Collections.shuffle(Arrays.asList(availableSlots));
            Collections.shuffle(deck);
            for (int i = 0; i < Math.min(deck.size(), availableSlots.length); i++)
                table.placeCard(deck.remove(0), availableSlots[i]);

        } finally {
            // Unlock locks.
            deckLock.writeLock().unlock();
            table.unlockSlots(availableSlots, true);
        }

        return availableSlots.length > 0;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try{
            Thread.sleep(reshuffleTime - System.currentTimeMillis() <= env.config.turnTimeoutWarningMillis ? TEN_MILLI_SECONDS : SECOND);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the timer and the timer display.
     *
     * @param reset - true iff the timer should be reset.
     */
    private void updateTimerDisplay(boolean reset) {
        table.tableReady = false;

        long currentMillis = System.currentTimeMillis();

        // Update players freeze timers if needed.
        for (Player player : players)
            env.ui.setFreeze(player.getId(), formatTime(player.getFreezeTime() - currentMillis, false));

        // Update countdown display according to game mode and reset if needed.
        if (gameMode == Mode.Timer)
            updateTimer(reset, currentMillis);
        else if (gameMode == Mode.Elapsed)
            updateElapsed(reset, currentMillis);

    }

    /**
     * Reset and/or update the countdown and the countdown display in Timer mode.
     *
     * @param reset         - true iff the countdown should be reset.
     * @param currentMillis - the current time in milliseconds.
     * @post - the countdown display is updated.
     */
    private void updateTimer(boolean reset, long currentMillis) {
        if (reset) {
            reshuffleTime = currentMillis + env.config.turnTimeoutMillis;
            for (Player player : players)
                player.setFreezeTime(-1);
        }
        long delta = reshuffleTime - currentMillis;
        boolean warn = delta <= env.config.turnTimeoutWarningMillis;
        env.ui.setCountdown(formatTime(delta, warn), warn);
    }

    /**
     * Reset and/or update the elapsed timer and its display.
     *
     * @param reset         - true iff the elapsed timer should be reset.
     * @param currentMillis - the current time in milliseconds.
     * @post - the elapsed display is updated.
     */
    private void updateElapsed(boolean reset, long currentMillis) {
        if (reset)
            elapsedTime = currentMillis;
        env.ui.setElapsed(formatTime(currentMillis - elapsedTime, false));
    }

    /**
     * Format a time in milliseconds to a comfortable long value.
     *
     * @param millis - the time in milliseconds.
     * @param warn   - true iff the time should be formatted as a warning.
     * @return - the formatted time.
     */
    private long formatTime(long millis, boolean warn) {
        return millis < 0 ? 0 : warn ? millis : (millis + SECOND / 2) / SECOND * SECOND;
    }

    /**
     * Returns all the cards from the table to the deck.
     *
     * @post - the table is empty from cards.
     */
    protected void removeAllCardsFromTable() {
        table.tableReady = false;

        try {
            // Lock locks.
            table.lockAllSlots(true);
            deckLock.writeLock().lock();

            removeAllTokens();
            clearAllPlayersQueues();

            List<Integer> filledSlots = IntStream.rangeClosed(0, env.config.tableSize - 1).boxed().filter(slot -> table.slotToCard[slot] != null).collect(Collectors.toList());
            Collections.shuffle(filledSlots);

            for (int slot : filledSlots) {
                // Return card to deck
                deck.add(table.slotToCard[slot]);
                table.removeCard(slot);
            }

        } finally {
            // Unlock locks.
            deckLock.writeLock().unlock();
            table.unlockAllSlots(true);
        }
    }

    /**
     * Iterate each player's tokens set, remove tokens from set and from table.
     *
     * @post - all tokens are removed from table.
     * @post - all tokens are removed from players' sets.
     */
    private void removeAllTokens() {
        table.removeAllTokens();
        for (Set<Integer> set : playersTokens.values())
            set.clear();
    }

    /**
     * clear all players' queues.
     *
     * @post - all players' queues are empty.
     */
    private void clearAllPlayersQueues() {
        for (Player player : players)
            player.clearChosenSlots();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // Get max score.
        int maxScore = 0;
        int score = 0;
        for (Player player : players) {
            maxScore = Math.max(maxScore, player.getScore());
            score += player.getScore();
        }
        System.out.println(score);
        int finalMaxScore = maxScore;

        // Get winners and display them.
        List<Integer> winnersIds = Arrays.stream(players).filter(player -> player.getScore() == finalMaxScore).map(Player::getId).collect(Collectors.toList());
        env.ui.announceWinner(winnersIds.stream().mapToInt(i -> i).toArray());
    }

    /**
     * Iterate setsToCheckByPlayer and check if any of the sets is a legal set. If so, remove the set from the table and add it to setsToRemove.
     * Freeze players according to the legality of their sets.
     *
     * @post - setsToCheckByPlayer is empty.
     */
    private void examineSets() {
        try {
            queueLock.lock();
            while (!setsToCheckByPlayer.isEmpty()) {
                // Acquire next set to examine and its player.
                int nextPlayer = setsToCheckByPlayer.remove();
                Player player = players[nextPlayer];
                List<Integer> possibleSet = new ArrayList<>(playersTokens.get(nextPlayer));

                try {
                    // Lock locks.
                    table.lockAllSlots(false);

                    // Check the set for legality
                    int[] slotsToExamine = possibleSet.stream().mapToInt(Integer::intValue).toArray();
                    int[] cardsToExamine = possibleSet.stream().mapToInt(Integer::intValue).map(slot -> table.slotToCard[slot]).toArray();


                    // Check if any tokens were removed from the set while another set was being examined.
                    if (cardsToExamine.length != env.config.featureSize) {
                        player.examined = false;
                        continue;
                    }

                    // Check if the set is legal.
                    boolean isLegalSet = env.util.testSet(cardsToExamine);

                    // If legal, remove tokens from relevant sets and add them to setsToRemove.
                    if (isLegalSet) {
                        setsToRemove.add(Arrays.stream(slotsToExamine).boxed().toArray(Integer[]::new));
                        removeWinningTokens(slotsToExamine);
                        player.point();
                    } else // If not legal, penalize player.
                        player.penalty();

                } finally {
                    // Unlock locks.
                    table.unlockAllSlots(false);
                }
            }
        } finally {
            queueLock.unlock();
        }
        // Pause the game from further actions.
        table.tableReady = false;
    }


    /**
     * Remove tokens of legal set from playersTokens and table.
     *
     * @param winningSlots - the slots of the legal set.
     * @post - each set in playersTokens contains only tokens that are not in winningSlots.
     * @post - if any token was removed from player's set, its examined field is set to false.
     */
    private void removeWinningTokens(int[] winningSlots) {
        table.tableReady = false;

        for (Integer playerId : playersTokens.keySet())
            for (int slot : winningSlots)
                if (playersTokens.get(playerId).remove(slot))
                    players[playerId].examined = false;
    }

    /**
     * given a slot by the player, add it to set and place token if missing, remove if exists.
     *
     * @param playerId - the player's id.
     * @param slot     - the slot to add to the set.
     */
    public void toggleToken(int playerId, int slot) {

        Set<Integer> playerSet = playersTokens.get(playerId);
        boolean containsSlot = playerSet.contains(slot);

        if (playerSet.size() < env.config.featureSize) {
            if (containsSlot)
                removeToken(playerId, slot);
            else {
                addToken(playerId, slot);

                // Set size is now env.config.featureSize, so we can check if it's a legal set.
                if (playerSet.size() == env.config.featureSize){
                    // Add player to setsToCheckByPlayer.
                    try {
                        queueLock.lock();
                        setsToCheckByPlayer.add(playerId);
                    } finally {
                        queueLock.unlock();
                    }

                    players[playerId].examined = true;

                    // Wakes the dealer thread to check the set.
                    dealerThread.interrupt();
                }
            }
        } else if (containsSlot)
            removeToken(playerId, slot);
    }

    /**
     * Add a token to a player's set and to the table.
     *
     * @param playerId - the id of the player.
     * @param slot     - the slot of the token.
     * @pre - slot on table is not null.
     * @pre - player's set does not contain slot
     * @pre - 0 <= player's set size < env.config.featureSize.
     * @post - player's set contains slot and its size
     * @post - 1 <= player's set size <= env.config.featureSize.
     */
    protected void addToken(int playerId, int slot) {
        playersTokens.get(playerId).add(slot);
        table.placeToken(playerId, slot);
    }

    /**
     * Add a token to a player's set and to the table.
     *
     * @param playerId - the id of the player.
     * @param slot     - the slot of the token.
     * @pre - slot on table is not null.
     * @pre - player's set does contains slot
     * @pre - 1 <= player's set size <= env.config.featureSize.
     * @post - player's set does not contain slot
     * @post - 0 <= player's set size < env.config.featureSize.
     */
    protected void removeToken(int playerId, int slot) {
        playersTokens.get(playerId).remove(slot);
        table.removeToken(playerId, slot);
    }

    /**
     * @return - playersTokens (for testing)
     */
    public HashMap<Integer, Set<Integer>> getPlayersTokens() {
        return playersTokens;
    }

    /**
     * @return - setsToCheckByPlayer (for testing)
     */
    public Queue<Integer> getSetsToCheckByPlayer() {
        return setsToCheckByPlayer;
    }

    /**
     * @return - setsToRemove (for testing)
     */
    public Queue<Integer[]> getSetsToRemove() {
        return setsToRemove;
    }

    /**
     * @return - terminate state (for testing)
     */
    public boolean getTerminate() {
        return terminate;
    }

}
