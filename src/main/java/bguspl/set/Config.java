package bguspl.set;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Properties;

/**
 * This class holds all the game's configuration data
 */
public class Config {

    /**
     * The number of features of each card.
     */
    public final int featureCount;

    /**
     * The number of possible values for each feature.
     */
    public final int featureSize;

    /**
     * The total number of cards in the deck.
     */
    public final int deckSize;

    /**
     * The number of columns in the table grid.
     */
    public final int columns;

    /**
     * The number of rows in the table grid.
     */
    public final int rows;

    /**
     * The total number of cells in the table grid.
     */
    public final int tableSize;

    /**
     * The width (in pixels) of each cell in the table grid.
     */
    public final int cellWidth;

    /**
     * The height (in pixels) of each cell in the table grid.
     */
    public final int cellHeight;

    /**
     * The size of the font.
     */
    public final int fontSize;

    /**
     * The number of human players in the game.
     */
    public final int humanPlayers;

    /**
     * The number of AI (computer) players in the game.
     */
    public final int computerPlayers;

    /**
     * The total number of players (human + computer) in the game.
     */
    public final int players;

    /**
     * An array containing the names of the players.
     */
    public final String[] playerNames;

    /**
     * If true, print the features and slot ids of all legal sets of the cards that are placed on the table.
     */
    public final boolean hints;

    /**
     * The time in milliseconds to wait for a legal set to be found before the dealer reshuffles the deck.
     */
    public final long turnTimeoutMillis;

    /**
     * The time in milliseconds a player gets frozen for as penalty for marking an illegal set.
     */
    public final long penaltyFreezeMillis;

    /**
     * The time in milliseconds a player ges frozen for when marking a legal set.
     */
    public final long pointFreezeMillis;

    /**
     * The time in milliseconds the dealer pauses before placing / removing a card from the table.
     */
    public final long tableDelayMillis;

    private final int[][] playerKeys;
    private static final String[] playerKeysDefaults = {
            "81,87,69,82,65,83,68,70,90,88,67,86",
            "85,73,79,80,74,75,76,59,77,44,46,47"};


    /**
     * Attempts to read the config properties from the current working directory. Otherwise, tries to load them
     * as a resource.
     *
     * @param filename - the name of the configuration file.
     *
     * @return         - a properties object with the configuration file contents.
     */
    private static Properties loadProperties(String filename) {

        Properties properties = new Properties();

        try (InputStream is = Files.newInputStream(Paths.get(filename))) {
            properties.load(is);
        } catch (IOException e) {
            System.out.printf("Warning: cannot read config file %s trying as a resource.%n", filename);
            try (InputStream is = Config.class.getClassLoader().getResourceAsStream(filename)) {
                properties.load(is);
            } catch (IOException | InvalidPathException ex) {
                System.out.println("Warning: cannot read config file from the resources directory either. Using defaults.");
            }
        }

        return properties;
    }

    public Config(String configFilename) {

        this(loadProperties(configFilename));
    }

    public Config(Properties properties) {

        // cards data
        featureSize = Integer.parseInt(properties.getProperty("FeatureSize", "3"));
        featureCount = Integer.parseInt(properties.getProperty("FeatureCount", "4"));
        deckSize = (int) Math.pow(featureSize, featureCount);

        // gameplay settings
        humanPlayers = Integer.parseInt(properties.getProperty("HumanPlayers", "2"));
        computerPlayers = Integer.parseInt(properties.getProperty("ComputerPlayers", "0"));
        players = humanPlayers + computerPlayers;

        hints = Boolean.parseBoolean(properties.getProperty("Hints", "false"));
        turnTimeoutMillis = (long) (Double.parseDouble(properties.getProperty("TurnTimeoutSeconds", "60")) * 1000.0);
        pointFreezeMillis = (long) (Double.parseDouble(properties.getProperty("PointFreezeSeconds", "1")) * 1000.0);
        penaltyFreezeMillis = (long) (Double.parseDouble(properties.getProperty("PenaltyFreezeSeconds", "3")) * 1000.0);
        tableDelayMillis = (long) (Double.parseDouble(properties.getProperty("TableDelaySeconds", "0.1")) * 1000.0);

        // ui data
        String[] names = properties.getProperty("PlayerNames", "Player 1, Player 2").split(",");
        playerNames = new String[players];
        Arrays.setAll(playerNames, i -> i < names.length ? names[i] : "Player " + (i + 1));

        rows = Integer.parseInt(properties.getProperty("Rows", "3"));
        columns = Integer.parseInt(properties.getProperty("Columns", "4"));
        tableSize = rows * columns;
        cellWidth = Integer.parseInt(properties.getProperty("CellWidth", "258"));
        cellHeight = Integer.parseInt(properties.getProperty("CellHeight", "167"));
        fontSize = Integer.parseInt(properties.getProperty("FontSize", "40"));

        // keyboard input data
        playerKeys = new int[players][rows * columns];
        for (int i = 0; i < players; i++) {
            String defaultCodes = "";
            if (i < 2) defaultCodes = playerKeysDefaults[i];
            String playerKeysString = properties.getProperty("PlayerKeys" + (i + 1), defaultCodes);
            if (playerKeysString.length() > 0) {
                String[] codes = playerKeysString.split(",");
                if (codes.length != tableSize)
                    System.out.printf("Warning: player %d keys (%d) mismatch table size (%d).%n", i + 1, codes.length, tableSize);
                for (int j = 0; j < Math.min(codes.length, tableSize); ++j) // parse the key codes string
                    playerKeys[i][j] = Integer.parseInt(codes[j]);
            }
        }
    }

    public int[] playerKeys(int player) {
        return playerKeys[player];
    }
}
