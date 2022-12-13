package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.getScore() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, ""), ui, util);
        player = new Player(env, dealer, table, 0, false);
        assertInvariants();

        table.tableReady = true;
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {

        // force table.countCards to return 3
        when(table.countCards()).thenReturn(3); // this part is just for demonstration

        // calculate the expected score for later
        int expectedScore = player.getScore() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.getScore());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }

    @Test
    void terminate() {
        player.terminate();
        assertTrue(player.terminate);
    }

    @Test
    void keyPress_ExamineShouldFail() {
        player.examined = true;
        int chosenSlotsSize = player.chosenSlots.size();

        player.keyPressed(0);

        assertEquals(player.chosenSlots.size(), chosenSlotsSize);
        assertFalse(player.chosenSlots.contains(0));
    }

    @Test
    void keyPress_TableReadyShouldFail(){
        table.tableReady = false;
        int chosenSlotsSize = player.chosenSlots.size();

        player.keyPressed(0);

        assertEquals(player.chosenSlots.size(), chosenSlotsSize);
        assertFalse(player.chosenSlots.contains(0));
    }

    @Test
    void keyPress_FreezeTimeShouldFail(){
        player.freezeTime = Long.MAX_VALUE;
        int chosenSlotsSize = player.chosenSlots.size();

        player.keyPressed(0);

        assertEquals(player.chosenSlots.size(), chosenSlotsSize);
        assertFalse(player.chosenSlots.contains(0));
    }

    @Test
    void keyPress_ChosenSlotsSizeShouldFail(){
        for (int i = 0; i < 3; i++)
            player.chosenSlots.add(i);
        int chosenSlotsSize = player.chosenSlots.size();

        player.keyPressed(3);

        assertEquals(player.chosenSlots.size(), chosenSlotsSize);
        assertFalse(player.chosenSlots.contains(3));
    }

    @Test
    void keyPress_shouldPass(){
        assertEquals(player.chosenSlots.size(), 0);

        int chosenSlotsSize = player.chosenSlots.size();

        player.keyPressed(0);

        assertEquals(player.chosenSlots.size(), chosenSlotsSize + 1);
        assertTrue(player.chosenSlots.contains(0));
    }

}