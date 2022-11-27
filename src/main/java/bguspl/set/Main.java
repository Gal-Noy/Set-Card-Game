package bguspl.set;

import bguspl.set.ex.Dealer;
import bguspl.set.ex.Player;
import bguspl.set.ex.Table;

import java.awt.*;

/**
 * This class contains the game's main function.
 */
public class Main {

    /**
     * The game's main function. Creates all data structures and initializes the threads.
     *
     * @param args - unused.
     */
    public static void main(String[] args) {

        // create the game environment objects
        Config config = new Config("config.properties");
        UserInterfaceImpl ui = new UserInterfaceImpl(config);
        EventQueue.invokeLater(() -> ui.setVisible(true));
        Env env = new Env(config, ui, new UtilImpl(config));

        // create the game entities
        Player[] players = new Player[env.config.players];
        Table table = new Table(env);
        Dealer dealer = new Dealer(env, table, players);
        for (int i = 0; i < players.length; i++)
            players[i] = new Player(env, dealer, table, i, i < env.config.humanPlayers);
        ui.addKeyListener(new InputManager(env, players));
        ui.addWindowListener(new WindowManager(dealer));

        // start the dealer thread
        Thread dealerThread = new Thread(dealer, "dealer");
        dealerThread.start();

        try { dealerThread.join(); } catch (InterruptedException ignored) {}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }
}
