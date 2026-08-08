package razen.microforge.core.util;

import java.util.Locale;
import java.util.function.Consumer;

public class TimeLogger {
    private final Consumer<String> log;
    private long startTime;

    public TimeLogger(Consumer<String> log) {
        this.log = log;
    }

    public void start() {
        this.startTime = System.nanoTime();
    }

    public void log(String message) {
        var elapsedMillis = (System.nanoTime() - this.startTime) / 1_000_000.0;
        this.log.accept(String.format(Locale.ROOT, "%s took %.1f ms.", message, elapsedMillis));
        this.start();
    }

    public static TimeLogger start(Consumer<String> log) {
        var timeLogger = new TimeLogger(log);
        timeLogger.start();
        return timeLogger;
    }
}
