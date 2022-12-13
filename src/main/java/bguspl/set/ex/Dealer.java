package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

    private final ReadWriteLock deckLock;


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
        this.deckLock = new ReentrantReadWriteLock();
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
            try {
                Thread.sleep(SECOND / players.length);
            } catch (InterruptedException ignored) {
            }
        }

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }

        announceWinners();

        if (!terminate) terminate();

        for (int i = players.length - 1; i >= 0; i--)
            try {
                players[i].getThread().join();
            } catch (InterruptedException ignored) {
            }


        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            examineSets(); // Main logic
            table.tableReady = false;
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
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
            try {
                Thread.sleep(SECOND / players.length);
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
        boolean setsLeft;
        try{
            deckLock.readLock().lock();
            setsLeft = env.util.findSets(deck, 1).size() == 0;
        } finally {
            deckLock.readLock().unlock();
        }
        return setsLeft;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */
    private void removeCardsFromTable() {
        table.tableReady = false;

        while (!setsToRemove.isEmpty()) {
            int[] setToRemove = setsToRemove.remove();
            for (int slot : setToRemove) {
                try{
                    table.slotLocks[slot].writeLock().lock();
                    deckLock.writeLock().lock();

                    deck.remove(table.slotToCard[slot]);
                    table.removeCard(slot);
                }
                finally {
                    deckLock.writeLock().unlock();
                    table.slotLocks[slot].writeLock().unlock();
                }
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
            List<Integer> cardsOnTable = Arrays.stream(table.slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
            boolean setsAvailable = env.util.findSets(cardsOnTable, 1).size() > 0;
            reshuffleTime = !setsAvailable ? System.currentTimeMillis() : Long.MAX_VALUE;
        }

        if (tableFilled && !shouldFinish()) {
            updateTimerDisplay(true);
            table.hints();
        }
    }

    private boolean shuffleAndDeal() {
        boolean tableFilled;
        try{
            for (int i = 0; i < table.slotToCard.length; i++)
                table.slotLocks[i].writeLock().lock();
            deckLock.writeLock().lock();

            List<Integer> availableSlots = IntStream.rangeClosed(0, env.config.tableSize - 1).boxed().filter(slot -> table.slotToCard[slot] == null).collect(Collectors.toList());
            for (int i = 0; i < availableSlots.size() && !deck.isEmpty(); i++) {
                int slot = availableSlots.get(i);
                int card = (int) (Math.random() * deck.size());
                table.slotToCard[slot] = card;
                table.placeCard(deck.get(card), slot);
                deck.remove(card);
            }
            tableFilled = !availableSlots.isEmpty();
        }
        finally {
            deckLock.writeLock().unlock();
            for (int i = table.slotToCard.length - 1; i >= 0; i--)
                table.slotLocks[i].writeLock().unlock();
        }
        return tableFilled;
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

        for (Player player : players)
            env.ui.setFreeze(player.getId(), formatTime(player.getFreezeTime() - currentMillis, false));

        if (gameMode == Mode.Timer) {
            if (reset) {
                reshuffleTime = currentMillis + env.config.turnTimeoutMillis;
                for (Player player : players)
                    player.setFreezeTime(-1);
            }
            long delta = reshuffleTime - currentMillis;
            boolean warn = delta <= env.config.turnTimeoutWarningMillis;
            env.ui.setCountdown(formatTime(delta, warn), warn);

        } else if (gameMode == Mode.Elapsed) {
            if (reset) {
                elapsedTime = currentMillis;
            }
            env.ui.setElapsed(formatTime(currentMillis - elapsedTime, false));
        }
    }

    private long formatTime(long millis, boolean warn) {
        return millis < 0 ? 0 : warn ? millis : (millis + SECOND / 2) / SECOND * SECOND;
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        table.tableReady = false;
        try{
            for (int i = 0; i < table.slotToCard.length; i++)
                table.slotLocks[i].writeLock().lock();

            removeAllTokens();

            List<Integer> occupiedSlots = IntStream.rangeClosed(0, env.config.tableSize - 1).boxed().filter(slot -> table.slotToCard[slot] != null).collect(Collectors.toList());
            for (int slot : occupiedSlots) {
                table.cardToSlot[table.slotToCard[slot]] = null;
                table.removeCard(slot);
            }
        }
        finally {
            for (int i = table.slotToCard.length - 1; i >= 0; i--)
                table.slotLocks[i].writeLock().unlock();
        }
    }

    // Iterate each player's tokens set, remove tokens from set and from table
    private void removeAllTokens() {
        table.removeAllTokens();
        for (Set<Integer> set : playersTokens.values())
            set.clear();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        int maxScore = 0;
        int sum = 0;
        for (Player player : players) {
            maxScore = Math.max(maxScore, player.getScore());
            sum += player.getScore();
        }
        System.out.println("scores " + sum);
        int finalMaxScore = maxScore;
        List<Integer> winnersIds = Arrays.stream(players).filter(player -> player.getScore() == finalMaxScore).map(Player::getId).collect(Collectors.toList());
        env.ui.announceWinner(winnersIds.stream().mapToInt(i -> i).toArray());
    }

    private void examineSets() {
        while (!setsToCheckByPlayer.isEmpty()) {
            int nextPlayer = setsToCheckByPlayer.remove();
            List<Integer> possibleSet = new ArrayList<>(playersTokens.get(nextPlayer));

            try{
                for (Integer slot : possibleSet) table.slotLocks[slot].readLock().lock();

                // Check the set for legality
                int[] slotsToExamine = possibleSet.stream().mapToInt(Integer::intValue).toArray();
                int[] cardsToExamine = possibleSet.stream().mapToInt(Integer::intValue).map(slot -> table.slotToCard[slot]).toArray();

                Player player = players[nextPlayer];

                // common tokens with previously removed set
                if (cardsToExamine.length != env.config.featureSize) {
                    player.examined = false;
                    continue;
                }

                boolean isLegalSet = env.util.testSet(cardsToExamine);

                if (isLegalSet) { // if legal, remove tokens from relevant sets
                    setsToRemove.add(slotsToExamine);
                    removeWinningTokens(slotsToExamine);
                    player.point();
                } else
                    player.penalty();
            }
            finally {
                for (int i = possibleSet.size() - 1; i >= 0; i--)
                    table.slotLocks[possibleSet.get(i)].readLock().unlock();
            }
        }
        table.tableReady = false;
    }

    private void removeWinningTokens(int[] winningSlots) {
        table.tableReady = false;
        for (Integer playerId : playersTokens.keySet()) {
            for (int slot : winningSlots)
                if (playersTokens.get(playerId).remove(slot))
                    players[playerId].examined = false;
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
                    players[playerId].examined = true;
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
