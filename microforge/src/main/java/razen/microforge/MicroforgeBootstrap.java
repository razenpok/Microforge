package razen.microforge;

import com.fs.state.AppDriver;

final class MicroforgeBootstrap {
    private static boolean started;

    private MicroforgeBootstrap() {
    }

    static synchronized void start() {
        if (started) {
            return;
        }

        AppDriver.getInstance().addState(new MicroforgeLoaderState());
        started = true;
    }
}
