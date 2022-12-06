package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    /**
     * For each player, hold current pressed slots
     */
    private HashMap<Integer, Set<Integer>> playersTokens;
    /**
     * Queue for every player with a possible set, in order of choosing
     */
    private Queue<Integer> possibleSets;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.playersTokens = new HashMap<>();
        this.possibleSets = new ArrayDeque<>();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());

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
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (Player player : players) {
            player.terminate();
        }
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
        // TODO implement
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        List<Integer> availableSlots = getAvailableSlots();
        List<Integer> availableCards = getAvailableCards();
        for (int slot : availableSlots) {
            int card = (int) (Math.random() * availableCards.size());
            table.slotToCard[slot] = card;
            table.placeCard(card, slot);
            availableCards.remove(card);
        }
        if (!availableSlots.isEmpty())
            updateTimerDisplay(true);
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
            Thread.sleep(1000);
        } catch (Exception e) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset)
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        else {
            long delta = reshuffleTime - System.currentTimeMillis();
            env.ui.setCountdown(delta, env.config.turnTimeoutWarningMillis < delta);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        List<Integer> occupiedSlots = getOccupiedSlots();
        for (int slot : occupiedSlots) {
            table.cardToSlot[table.slotToCard[slot]] = null;
            table.removeCard(slot);
        }
        updateTimerDisplay(true);
    }

    private List<Integer> getOccupiedSlots(){
        List<Integer> output = new ArrayList<>();
        for (int i = 0; i < table.slotToCard.length; i++) {
            if (table.slotToCard[i] != null)
                output.add(i);
        }
        return output;
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }

    public Set<Integer> getPlayerSet(int playerId) {
        return playersTokens.get(playerId);
    }

    public void addPossible(int playerId) {
        possibleSets.add(playerId);
    }

    private void examineSets() {
        while (!possibleSets.isEmpty()) {
            int nextPlayer = possibleSets.remove();
            Set<Integer> possibleSet = playersTokens.get(nextPlayer);

            // common tokens with previously removed set
            if (possibleSet.size() != env.config.featureSize) continue;

            try {
                // Acquire locks to slots
                for (Integer slot : possibleSet)
                    table.getSlotLock(slot).acquire();

                // Check the set for legality
                int[] cards = possibleSet.stream().mapToInt(Integer::intValue).toArray();
                boolean isLegalSet = env.util.testSet(cards);

                freezePlayer(nextPlayer, isLegalSet);

                // if legal, remove tokens from sets
                if (isLegalSet)
                    removeTokensFromSets(cards);

            } catch (InterruptedException e) {
                for (Integer slot : possibleSet)
                    table.getSlotLock(slot).release();
            }
        }
    }

    private void freezePlayer(int playerId, boolean isLegalSet) {
        Thread playerThread = players[playerId].getThread();
        long freezeTime = isLegalSet ? env.config.pointFreezeMillis : env.config.penaltyFreezeMillis;
        try {
            playerThread.wait(freezeTime);
            env.ui.setFreeze(playerId, freezeTime);
        } catch (InterruptedException e) {
            System.out.println("THERE IS AN ERROR HERE!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    private void removeTokensFromSets(int[] cards) {
        for (Set<Integer> set : playersTokens.values()) {
            for (int slot : cards)
                set.remove(slot);
        }
    }
}
