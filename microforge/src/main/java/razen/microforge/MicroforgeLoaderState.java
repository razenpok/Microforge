package razen.microforge;

import com.fs.state.AppState;

import java.util.Map;

import org.lwjgl.Sys;
import org.apache.log4j.Logger;
import razen.microforge.core.modbuild.ModBuildTarget;
import razen.microforge.core.modbuild.ModProcessor;
import razen.microforge.core.mods.ModManager;
import razen.microforge.core.mods.MicroforgeVersionCompatibility;
import razen.microforge.core.patch.GameVersion;
import razen.microforge.core.patch.PatchCompatibilityException;

public class MicroforgeLoaderState implements AppState {
    private static final Logger LOG = Logger.getLogger(MicroforgeLoaderState.class);
    public static final String STATE_ID = MicroforgeLoaderState.class.getCanonicalName();
    private final LoadingScreen screen = new LoadingScreen();

    public String getID() {
        return STATE_ID;
    }

    public void init(Map session) {
        try {
            screen.initialize();
        } catch (Exception e) {
            throw shutdown("Microforge could not initialize its loading screen.\n"
                    + "Check starsector.log for more info.", e);
        }

        var modManager = verify();
        try {
            var buildOutputRoot = Paths.getModPath().resolve("out/build/classes");
            var buildProcessor = new ModProcessor(buildOutputRoot, Paths.getEcjJar(),
                    Paths.gameClasspathJars(), modManager.getEnabledMods());
            var targets = buildProcessor.findTargets();
            if (targets.isEmpty()) {
                return;
            }

            screen.render(0.0F, null);
            buildProcessor.compileAll(targets, (target, completed, total) ->
                    screen.render((float) completed / total,
                            target == null ? null : compilingMessage(target)));
        } catch (Exception e) {
            throw shutdown("Microforge could not build the enabled mods.\n"
                    + "Check starsector.log for more info.", e);
        }
    }

    public String traverse() {
        return "Title Screen State";
    }

    private static String compilingMessage(ModBuildTarget target) {
        return "Compiling " + target.displayName() + "...";
    }

    private static ModManager verify() {
        final ModManager modManager;
        try {
            modManager = ModManager.getInstance();
            modManager.updateList();
        } catch (Exception e) {
            throw shutdown("Microforge could not read the enabled mods.\n"
                    + "Check starsector.log for more info.", e);
        }

        if (!modManager.isEnabled(ModManager.MICROFORGE_ID)) {
            throw shutdown(
                    "Microforge is disabled in the mod list.\nEnable Microforge and restart the game.", null);
        }

        var microforge = modManager.getAvailableMod(ModManager.MICROFORGE_ID);
        if (microforge == null) {
            throw shutdown("Microforge was not found in the mod manager.\n"
                    + "Reinstall Microforge and restart the game.", null);
        }
        var versionCompatibility = MicroforgeVersionCompatibility.inspect(
                microforge, modManager.getEnabledMods());
        if (!versionCompatibility.isCompatible()) {
            try {
                modManager.disableMods(versionCompatibility.incompatibleModIds());
            } catch (Exception e) {
                throw shutdown("Microforge could not disable mods that declare an incompatible Microforge version.\n"
                        + "Check starsector.log for more info.", e);
            }
            throw shutdown(MicroforgeVersionMessages.cannotStart(versionCompatibility), null);
        }

        final boolean current;
        try {
            current = GameApiPatcher.isPreparedPatchCurrent(modManager, GameVersion.load());
        } catch (PatchCompatibilityException e) {
            throw shutdown(e.getMessage(), null);
        } catch (Exception e) {
            throw shutdown("Microforge could not verify the Starsector API patches.\n"
                    + "Check starsector.log for more info.", e);
        }
        if (!current) {
            throw shutdown("The mod selection changed after Microforge started.\n"
                    + "Restart the game to apply it.", null);
        }
        return modManager;
    }

    private static IllegalStateException shutdown(String message, Exception cause) {
        if (cause != null) {
            LOG.error("Microforge encountered an unrecoverable error during game startup.", cause);
        }
        Sys.alert("Microforge", message);
        System.exit(1);
        return new IllegalStateException(message, cause);
    }

    public void goToState(String stateId) {
    }
}
