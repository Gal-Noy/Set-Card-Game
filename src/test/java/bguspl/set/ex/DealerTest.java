package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    Dealer dealer;
    Player[] players;

    @Mock
    private Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Logger logger;
    @Mock
    private Config config;

    void assertInvariants() {
        assertTrue(dealer.deck.size() <= 81);
        assertTrue(dealer.getSetsToCheckByPlayer().size() <= players.length);
    }

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys2", "85,73,79,80");
        properties.put("PointFreezeSeconds", "1");
        properties.put("PenaltyFreezeSeconds", "3");
        TableTest.MockLogger logger = new TableTest.MockLogger();
        config = new Config(logger, properties);
        ui = new DealerTest.MockUserInterface();
        Env env = new Env(logger, config, ui, util);
        players = new Player[4];
        for (int i = 0; i < players.length; i++)
            players[i] = new Player(env, dealer, table, i, true);
        table = new Table(env);
        dealer = new Dealer(env, table, players);

        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    void putSomeCards(){
        for (int i = 0; i < 3; i++){
            table.slotToCard[i] = i;
            table.cardToSlot[i] = i;
        }
    }

    @Test
    void terminate() {
        dealer.terminate();
        assertTrue(dealer.getTerminate());
    }

    @Test
    void removeCardsFromTable() {
        putSomeCards();
        Integer[] legalSet = new Integer[]{0, 1, 2};
        int c0 = table.cardToSlot[0];
        int c1 = table.cardToSlot[1];
        int c2 = table.cardToSlot[2];

        dealer.getSetsToRemove().add(legalSet);
        dealer.removeCardsFromTable();

        assertFalse(dealer.deck.contains(c0));
        assertFalse(dealer.deck.contains(c1));
        assertFalse(dealer.deck.contains(c2));

        assertNull(table.slotToCard[0]);
        assertNull(table.slotToCard[1]);
        assertNull(table.slotToCard[2]);
    }

    @Test
    void addToken() {
        putSomeCards();

        assertFalse(dealer.getPlayersTokens().get(0).contains(0));
        dealer.addToken(0, 0);
        assertTrue(dealer.getPlayersTokens().get(0).contains(0));
    }

    @Test
    void removeToken() {
        putSomeCards();

        dealer.addToken(0, 0);
        assertTrue(dealer.getPlayersTokens().get(0).contains(0));
        dealer.removeToken(0, 0);
        assertFalse(dealer.getPlayersTokens().get(0).contains(0));

    }

    static class MockUserInterface implements UserInterface {
        @Override
        public void placeCard(int card, int slot) {
        }

        @Override
        public void removeCard(int slot) {
        }

        @Override
        public void setCountdown(long millies, boolean warn) {
        }

        @Override
        public void setElapsed(long millies) {
        }

        @Override
        public void setScore(int player, int score) {
        }

        @Override
        public void setFreeze(int player, long millies) {
        }

        @Override
        public void placeToken(int player, int slot) {
        }

        @Override
        public void removeTokens() {
        }

        @Override
        public void removeTokens(int slot) {
        }

        @Override
        public void removeToken(int player, int slot) {
        }

        @Override
        public void announceWinner(int[] players) {
        }
    }


}