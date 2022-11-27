package bguspl.set;

import bguspl.set.ex.Dealer;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

/**
 * Handles windows events (e.g. closing the game window with the X button).
 */
public class WindowManager implements WindowListener {

    /**
     * The main thread.
     */
    private final Thread mainThread;

    /**
     * The dealer object.
     */
    private final Dealer dealer;

    public WindowManager(Dealer dealer) {
        mainThread = Thread.currentThread();
        this.dealer = dealer;
    }

    @Override
    public void windowOpened(WindowEvent e) {
        // Auto-generated method stub
    }

    @Override
    public void windowClosing(WindowEvent e) {
        dealer.terminate();
        try { mainThread.join(); } catch (InterruptedException ignored) {}
        System.out.println("Info: Thanks for playing... it was fun!");
    }

    @Override
    public void windowClosed(WindowEvent e) {
        // Auto-generated method stub
    }

    @Override
    public void windowIconified(WindowEvent e) {
        // Auto-generated method stub
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
        // Auto-generated method stub
    }

    @Override
    public void windowActivated(WindowEvent e) {
        // Auto-generated method stub
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
        // Auto-generated method stub
    }
}
