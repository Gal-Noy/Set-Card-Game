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

import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;

    ConcurrentLinkedQueue<Integer> chosenSlots;

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
    @Mock
    private Config config;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.getScore() >= 0);
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

        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, config, ui, util);
        player = new Player(env, dealer, table, 0, false);
        chosenSlots = player.getChosenSlots();

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
        assertTrue(player.getTerminate());
    }

    @Test
    void keyPress_TableReadyShouldFail(){
        table.tableReady = false;

        int chosenSlotsSize = chosenSlots.size();

        player.keyPressed(0);

        assertEquals(chosenSlots.size(), chosenSlotsSize);
        assertFalse(chosenSlots.contains(0));
    }

    @Test
    void keyPress_FreezeTimeShouldFail(){
        player.setFreezeTime(Long.MAX_VALUE);

        int chosenSlotsSize = chosenSlots.size();

        player.keyPressed(0);

        assertEquals(chosenSlots.size(), chosenSlotsSize);
        assertFalse(chosenSlots.contains(0));
    }

    @Test
    void keyPress_ChosenSlotsSizeShouldFail(){

        for (int i = 0; i < config.featureSize; i++)
            chosenSlots.add(i);
        int chosenSlotsSize = chosenSlots.size();

        player.keyPressed(3);

        assertEquals(chosenSlots.size(), chosenSlotsSize);
        assertFalse(chosenSlots.contains(3));
    }

    @Test
    void keyPress_shouldPass(){

        assertEquals(chosenSlots.size(), 0);

        int expectedSlotsSize = chosenSlots.size() + 1;

        player.keyPressed(0);

        assertEquals(chosenSlots.size(), expectedSlotsSize);
        assertTrue(chosenSlots.contains(0));
    }

    @Test
    void point_freezeTime(){
        long expectedFreezeTime = Long.sum(System.currentTimeMillis(), config.pointFreezeMillis);

        player.point();

        assertTrue(player.getFreezeTime() >= expectedFreezeTime);
    }

    @Test
    void penalty_freezeTime(){
        long expectedFreezeTime = Long.sum(System.currentTimeMillis(), config.penaltyFreezeMillis);

        player.penalty();

        assertTrue(player.getFreezeTime() >= expectedFreezeTime);
    }

    @Test
    void setFreezeTime(){
        long expectedFreezeTime = config.penaltyFreezeMillis;

        player.setFreezeTime(expectedFreezeTime);

        assertEquals(player.getFreezeTime(), expectedFreezeTime);
    }



}