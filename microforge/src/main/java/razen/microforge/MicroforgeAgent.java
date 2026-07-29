package razen.microforge;

import org.lwjgl.Sys;

import java.lang.instrument.Instrumentation;

public final class MicroforgeAgent {
    private MicroforgeAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try {
            EnabledMods.ensureMicroforgeEnabled();
        } catch (Exception e) {
            Sys.alert("Microforge", "Fatal: " + e.getMessage());
            System.exit(1);
        }

        MicroforgeBootstrap.start();
    }
}
