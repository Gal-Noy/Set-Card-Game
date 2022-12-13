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
import java.util.List;
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

    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;

    @Mock
    Player[] players;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(dealer.deck.size() <= 81);
        assertTrue(dealer.setsToCheckByPlayer.size() <= players.length);
    }

    @BeforeEach
    void setUp() {
        Env env = new Env(logger, new Config(logger, ""), ui, util);
        dealer = new Dealer(env, table, players);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void removeCardsFromTable(){
        Integer[] legalSet = new Integer[]{1,2,3};
        dealer.setsToRemove.add(legalSet);
        dealer.removeCardsFromTable();

        assertFalse(dealer.deck.contains(table.slotToCard[1]));
        assertFalse(dealer.deck.contains(table.slotToCard[1]));
        assertFalse(dealer.deck.contains(table.slotToCard[1]));
        assertNull(table.slotToCard[1]);
        assertNull(table.slotToCard[2]);
        assertNull(table.slotToCard[3]);
    }




}