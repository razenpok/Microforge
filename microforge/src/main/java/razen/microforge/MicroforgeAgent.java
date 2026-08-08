package razen.microforge;

import org.apache.log4j.Logger;
import org.lwjgl.Sys;
import razen.microforge.core.mods.ModManager;
import razen.microforge.core.mods.MicroforgeVersionCompatibility;
import razen.microforge.core.patch.GameVersion;
import razen.microforge.core.patch.PatchCompatibilityException;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

public final class MicroforgeAgent {
    private static final Logger LOG = Logger.getLogger(MicroforgeAgent.class);
    private static final String AGENT_LOADED_PROPERTY = "razen.microforge.agent.loaded";

    private MicroforgeAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.setProperty(AGENT_LOADED_PROPERTY, "true");
        try {
            var modManager = ModManager.getInstance();
            var microforge = modManager.getAvailableMod(ModManager.MICROFORGE_ID);
            if (microforge == null) {
                throw new IllegalStateException("Microforge was not found in the mod manager");
            }
            var ecjJar = new JarFile(Paths.findEcjJar(microforge.root()).toFile());
            instrumentation.appendToSystemClassLoaderSearch(ecjJar);

            var versionCompatibility = MicroforgeVersionCompatibility.inspect(
                    microforge, modManager.getEnabledMods());
            if (!versionCompatibility.isCompatible()) {
                modManager.disableMods(versionCompatibility.incompatibleModIds());
                var message = MicroforgeVersionMessages.modsDisabled(versionCompatibility);
                LOG.warn(message);
                Sys.alert("Microforge - Incompatible version", message);
            }

            var gameVersion = GameVersion.load();
            var patchResult = GameApiPatcher.build(modManager, gameVersion);
            if (patchResult.isPresent()) {
                var result = patchResult.get();
                var replacementJar = new JarFile(result.jar().toFile());
                instrumentation.appendToSystemClassLoaderSearch(replacementJar);
                instrumentation.addTransformer(new ReplacementTransformer(result.replacements()));
            }
            MicroforgeBootstrap.start();
        } catch (PatchCompatibilityException e) {
            Sys.alert("Microforge - Mod incompatibility", e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Microforge could not start.", e);
            Sys.alert("Microforge", "Microforge could not start.\nCheck starsector.log for more info.");
            System.exit(1);
        }
    }
}
