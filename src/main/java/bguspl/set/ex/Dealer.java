package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

enum Mode {
    Timer,
    FreePlay,
    Elapsed
}

/**
 * This class manages the dealer's threads and data
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

    private Thread dealerThread;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    private long elapsedTime = System.currentTimeMillis();
    /**
     * For each player, hold current pressed slots
     */
    private final ConcurrentHashMap<Integer, ConcurrentSkipListSet<Integer>> playersTokens;
    /**
     * Queue for every player with a possible set, in order of choosing
     */
    private final ConcurrentLinkedQueue<Integer> setsToCheckByPlayer;

    private final ConcurrentLinkedQueue<int[]> setsToRemove;

    private final Mode gameMode;

    private final long SECOND = 1000;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        this.deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());

        this.playersTokens = new ConcurrentHashMap<>();
        for (int i = 0; i < this.players.length; i++)
            playersTokens.put(i, new ConcurrentSkipListSet<>());
        this.setsToCheckByPlayer = new ConcurrentLinkedQueue<>();
        this.setsToRemove = new ConcurrentLinkedQueue<>();
        this.gameMode = env.config.turnTimeoutMillis > 0 ? Mode.Timer :
                env.config.turnTimeoutMillis < 0 ? Mode.FreePlay : Mode.Elapsed;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

        dealerThread = Thread.currentThread();
        for (Player player : players) {
            player.setThread(new Thread(player, "player " + player.getId()));
            player.getThread().start();
        }

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        if (!terminate) terminate();

        for (int i = players.length-1; i >= 0; i--)
            try {players[i].getThread().join();} catch (InterruptedException ignored) {}

        announceWinners();

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            examineSets(); // Main logic
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
            if (!table.tableReady) table.tableReady = true;
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (Player player : players)
            player.terminate();
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        table.tableReady = false;

        while (!setsToRemove.isEmpty()) {
            int[] setToRemove = setsToRemove.remove();
            for (int slot : setToRemove) {
                deck.remove(table.slotToCard[slot]);
                table.removeCard(slot, true);
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        table.tableReady = false;

        boolean tableFilled = shuffleAndDeal();

        if (gameMode != Mode.Timer) {
            List<Integer> cardsOnTable = new ArrayList<>();
            for (Integer card : table.slotToCard)
                if (card != null)
                    cardsOnTable.add(card);
            boolean setsAvailable = env.util.findSets(cardsOnTable, 1).size() > 0;
            reshuffleTime = !setsAvailable ? System.currentTimeMillis() : Long.MAX_VALUE;
        }

        if (tableFilled && !shouldFinish()) {
            updateTimerDisplay(true);
//            table.hints();
        }
    }

    private boolean shuffleAndDeal() {
        List<Integer> availableSlots = getAvailableSlots();
        List<Integer> availableCards = getAvailableCards();
        for (int i = 0; i < availableSlots.size() && !availableCards.isEmpty(); i++) {
            int slot = availableSlots.get(i);
            int card = (int) (Math.random() * availableCards.size());
            table.slotToCard[slot] = card;
            table.placeCard(availableCards.get(card), slot);
            availableCards.remove(card);
        }
        return !availableSlots.isEmpty();
    }

    private List<Integer> getAvailableSlots() {
        List<Integer> output = new ArrayList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] == null)
                output.add(i);
        }
        return output;
    }

    private List<Integer> getAvailableCards() {
        List<Integer> output = new ArrayList<>();
        for (int i = 0; i < table.cardToSlot.length; i++) {
            if (table.cardToSlot[i] == null)
                output.add(i);
        }
        return output;
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            Thread.sleep(SECOND);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        table.tableReady = false;

        long currentMillis = System.currentTimeMillis();

        if (gameMode == Mode.Timer) {
            if (reset) {
                reshuffleTime = currentMillis + env.config.turnTimeoutMillis;
                for (Player player : players)
                    player.setFreezeTime(-1);
            }
            long delta = reshuffleTime - currentMillis;
            env.ui.setCountdown(delta, env.config.turnTimeoutWarningMillis >= delta);

        } else if (gameMode == Mode.Elapsed) {
            if (reset)
                elapsedTime = currentMillis;
            env.ui.setElapsed(currentMillis - elapsedTime);
        }
        for (Player player : players)
            env.ui.setFreeze(player.getId(), player.getFreezeTime() - currentMillis);
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.tableReady = false;

        removeAllTokens();

        List<Integer> occupiedSlots = getOccupiedSlots();
        for (int slot : occupiedSlots) {
            table.cardToSlot[table.slotToCard[slot]] = null;
            table.removeCard(slot, false);
        }

        updateTimerDisplay(true);
    }

    private List<Integer> getOccupiedSlots() {
        List<Integer> output = new ArrayList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null)
                output.add(i);
        }
        return output;
    }

    private void removeAllTokens() {
        // Iterate each player's tokens set, remove tokens from set and from table
        for (Player player : players) {
            Set<Integer> playerSet = playersTokens.get(player.getId());
            for (int slot : playerSet)
                table.removeToken(player.getId(), slot);
            playerSet.clear();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        for (Player player : players)
            maxScore = Math.max(maxScore, player.getScore());
        List<Integer> winnersIds = new ArrayList<>();
        for (Player player : players)
            if (player.getScore() == maxScore)
                winnersIds.add(player.getId());
        env.ui.announceWinner(winnersIds.stream().mapToInt(i -> i).toArray());
    }

    private void examineSets() {
        while (!setsToCheckByPlayer.isEmpty()) {
            int nextPlayer = setsToCheckByPlayer.remove();
            Set<Integer> possibleSet = playersTokens.get(nextPlayer);

            // Check the set for legality
            int[] slotsToExamine = possibleSet.stream().mapToInt(Integer::intValue).toArray();
            int[] cardsToExamine = possibleSet.stream().mapToInt(Integer::intValue).map(slot -> table.slotToCard[slot]).toArray();

            // common tokens with previously removed set
            if (cardsToExamine.length != env.config.featureSize) continue;

            boolean isLegalSet = env.util.testSet(cardsToExamine);
            Player player = players[nextPlayer];


            if (isLegalSet) { // if legal, remove tokens from relevant sets
                setsToRemove.add(slotsToExamine);
                removeWinningTokens(slotsToExamine);
                player.point();
            } else
                player.penalty();
        }
    }

    private void removeWinningTokens(int[] winningSlots) {
        table.tableReady = false;
        for (Integer playerId : playersTokens.keySet()) {
            for (int slot : winningSlots)
                removeToken(playerId, slot);
        }
    }

    public void toggleToken(int playerId, int slot) {
        Set<Integer> playerSet = playersTokens.get(playerId);
        boolean containsSlot = playerSet.contains(slot);
        if (playerSet.size() < env.config.featureSize) {
            if (containsSlot)
                removeToken(playerId, slot);
            else {
                addToken(playerId, slot);

                // Updated to three
                if (playerSet.size() == env.config.featureSize) {
                    setsToCheckByPlayer.add(playerId);
                    dealerThread.interrupt(); // Wakes the dealer
                }
            }
        } else if (containsSlot)
            removeToken(playerId, slot);
    }

    private void addToken(int playerId, int slot) {
        playersTokens.get(playerId).add(slot);
        table.placeToken(playerId, slot);
    }

    private void removeToken(int playerId, int slot) {
        playersTokens.get(playerId).remove(slot);
        table.removeToken(playerId, slot);
    }

}
