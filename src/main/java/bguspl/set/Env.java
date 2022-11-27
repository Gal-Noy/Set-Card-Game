package bguspl.set;

public class Env {

    public final Config config;
    public final UserInterface ui;
    public final Util util;

    public Env(Config config, UserInterface ui, Util util) {
        this.config = config;
        this.ui = ui;
        this.util = util;
    }
}
