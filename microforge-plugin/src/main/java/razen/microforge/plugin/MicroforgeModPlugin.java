package razen.microforge.plugin;

import com.fs.starfarer.api.BaseModPlugin;
import org.lwjgl.Sys;

@SuppressWarnings("unused")
public class MicroforgeModPlugin extends BaseModPlugin {
    private static final String AGENT_LOADED_PROPERTY = "razen.microforge.agent.loaded";

    @Override
    public void onApplicationLoad() {
        if (Boolean.getBoolean(AGENT_LOADED_PROPERTY)) {
            return;
        }

        var message = "Microforge was not installed correctly (-javaagent missing).\n"
                + "Please follow the installation guide.";
        Sys.alert("Microforge", message);
        System.exit(1);
    }
}
