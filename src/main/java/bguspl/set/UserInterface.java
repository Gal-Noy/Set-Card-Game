package bguspl.set;

/**
 * This interface contains all methods used to display the graphical user interface.
 */
public interface UserInterface {
    /**
     * Draw the card image corresponding to the card id in the specified slot.
     * @param card - the card id.
     * @param slot - the slot number (for grid; slot = row*row.length + column).
     */
    void placeCard(int card, int slot);

    /**
     * Draw an empty card image in the specified slot.
     * @param slot - the slot number (for grid; slot = row*row.length + column).
     */
    void removeCard(int slot);

    /**
     * Set the timer digits to the specified time.
     * @param seconds - number of seconds to be shown.
     * @param warn - if true, seconds will be painted in red.
     */
    void setCountdown(int seconds, boolean warn);

    /**
     * Set the score for the relevent player in the player score panel.
     * @param player - the player id.
     * @param score - the score to value.
     */
    void setScore(int player, int score);

    /**
     * Set the player text in the score panel to show remaining freeze time.
     * If seconds > 0, show player name in red, and add freeze time.
     * If seconds <= 0, set player name to default black name without freeze.
     * @param player - the player id.
     * @param seconds - the freeze time in seconds.
     */
    void setFreeze(int player, int seconds);

    /**
     * Draw a player name text in the specified slot.
     * @param player - the card id.
     * @param slot - the slot number (for grid; slot = row*row.length + column).
     */
    void placeToken(int player, int slot);

    /**
     * Remove all players names text from all slot.
     */
    void removeTokens();

    /**
     * Remove all player names text in the specified slot.
     * @param slot - the slot number (for grid; slot = row*row.length + column).
     */
    void removeTokens(int slot);

    /**
     * Remove player name text in the specified slot.
     * @param player - the card id.
     * @param slot - the slot number (for grid; slot = row*row.length + column).
     */
    void removeToken(int player, int slot);

    /**
     * Hide player score panel from view and show text announcing the winner(s).
     * If players length == 1, declare him as a winner.
     * If players length > 1, declare tie between all players in players list.
     * @param players - the players ids.
     */
    void announceWinner(int[] players);
}
