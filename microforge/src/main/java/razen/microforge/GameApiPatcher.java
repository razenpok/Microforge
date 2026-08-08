package razen.microforge;

import org.apache.log4j.Logger;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.mods.ModManager;
import razen.microforge.core.mods.ModSpec;
import razen.microforge.core.mods.MicroforgeVersionCompatibility;
import razen.microforge.core.patch.ApiPatchInputs;
import razen.microforge.core.patch.ApiPatchPaths;
import razen.microforge.core.patch.GameVersion;
import razen.microforge.core.patch.GameVersionMarker;
import razen.microforge.core.patch.PatchApplier;
import razen.microforge.core.patch.PatchCompatibilityException;
import razen.microforge.core.patch.PatchFingerprint;
import razen.microforge.core.patch.SourceWorkspace;
import razen.microforge.core.patch.UnifiedPatch;
import razen.microforge.core.util.StringUtils;
import razen.microforge.core.util.TimeLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class GameApiPatcher {
    private static final Logger LOG = Logger.getLogger(GameApiPatcher.class);
    private static volatile String preparedPatchHash = "";

    private GameApiPatcher() {
    }

    static Optional<Result> build(ModManager modManager, GameVersion gameVersion) throws Exception {
        var totalTimer = TimeLogger.start(message -> LOG.info("Starsector API " + message));
        try {
            return tryBuild(modManager, gameVersion);
        } catch (PatchCompatibilityException e) {
            logCompatibilityFailure(e);
            throw e;
        } finally {
            totalTimer.log("patching");
        }
    }

    private static Optional<Result> tryBuild(ModManager modManager, GameVersion gameVersion) throws Exception {
        var timer = TimeLogger.start(message -> LOG.info("Starsector API " + message));
        var prepared = prepare(modManager, gameVersion, timer);
        if (!prepared.hasInputs()) {
            preparedPatchHash = prepared.hash();
            return Optional.empty();
        }

        var workspace = prepared.workspace();
        FileOperations.writeAtomically(workspace.mergedPatch(), prepared.patch().serialize());
        preparedPatchHash = prepared.hash();
        timer.log("patch output writing");
        if (prepared.patch().isEmpty()) {
            LOG.info("Enabled Starsector API patch inputs contain no source changes.");
            return Optional.empty();
        }

        if (!GameApiPatchCompiler.isUpToDate(workspace.replacementJar(), prepared.hash())) {
            var changedSources = PatchApplier.apply(workspace.sourceRoot(), prepared.patch()).stream()
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList();
            if (!Files.isDirectory(workspace.patchedRoot())) {
                throw new IOException("patched source directory was not created: " + workspace.patchedRoot());
            }
            timer.log("patch application");

            new GameApiPatchCompiler(prepared.enabledMods(), prepared.gameJarsDir())
                    .compile(workspace, changedSources, prepared.hash());
            timer.log("replacement compilation");
        } else {
            LOG.info("Using cached Starsector API replacements; merged patch unchanged.");
        }

        var replacements = GameApiPatchCompiler.readReplacements(workspace.replacementJar());
        if (replacements.isEmpty()) {
            throw new IOException("replacement jar contains no class files: " + workspace.replacementJar());
        }
        timer.log("replacement loading");
        return Optional.of(new Result(workspace.replacementJar(), replacements));
    }

    static boolean isPreparedPatchCurrent(ModManager modManager, GameVersion gameVersion) throws Exception {
        try {
            var timer = TimeLogger.start(message -> LOG.info("Starsector API " + message));
            return preparedPatchHash.equals(prepare(modManager, gameVersion, timer).hash());
        } catch (PatchCompatibilityException e) {
            logCompatibilityFailure(e);
            throw e;
        }
    }

    private static PreparedPatch prepare(ModManager modManager, GameVersion gameVersion, TimeLogger timer)
            throws IOException {
        var enabledMods = modManager.getEnabledMods();
        var microforge = modManager.getAvailableMod(ModManager.MICROFORGE_ID);
        if (microforge == null) {
            throw new IllegalStateException("Microforge was not found in the mod manager");
        }
        var microforgeRoot = microforge.root();
        var outputRoot = ApiPatchPaths.outputRoot(microforgeRoot);
        if (GameVersionMarker.resetIfStale(outputRoot, gameVersion)) {
            LOG.info("Cleared stale Starsector API patch cache at " + outputRoot + ".");
        }

        var versionCompatibility = MicroforgeVersionCompatibility.inspect(microforge, enabledMods);
        for (var requirement : versionCompatibility.incompatibleMods()) {
            LOG.warn("Ignoring Starsector API patch from " + requirement.mod().name()
                    + "; it declares Microforge " + requirement.requiredVersion()
                    + ", which is incompatible with installed version "
                    + versionCompatibility.installedVersion() + ".");
        }
        var patchMods = versionCompatibility.compatiblePatchMods(enabledMods);
        var inputs = ApiPatchInputs.discover(patchMods, gameVersion);
        timer.log("patch discovery");
        if (inputs.isEmpty()) {
            return PreparedPatch.empty(enabledMods, PatchFingerprint.hash(gameVersion, UnifiedPatch.empty()));
        }

        var gameJarsDir = Paths.findGameJarsDir();
        var workspace = ApiPatchPaths.workspace(microforgeRoot, gameJarsDir);
        var unpacked = SourceWorkspace.unpackIfAbsent(workspace.sourceZip(), workspace.sourceRoot());
        if (unpacked) {
            LOG.info("Unpacked " + workspace.sourceZip() + " to " + workspace.sourceRoot());
        } else {
            LOG.info("Using cached pristine Starsector API sources from " + workspace.sourceRoot());
        }
        timer.log("source preparation");

        var patch = inputs.generatePatch(workspace.sourceRoot());
        timer.log("patch input loading and merging");
        var hash = PatchFingerprint.hash(gameVersion, patch);
        timer.log("patch hashing");
        return new PreparedPatch(true, enabledMods, microforgeRoot, gameJarsDir, workspace, patch, hash);
    }

    private static void logCompatibilityFailure(PatchCompatibilityException exception) {
        var details = StringUtils.cleanString(exception.details());
        if (details == null) {
            LOG.error("A Starsector API patch is incompatible.", exception);
        } else if (exception.getCause() == null) {
            LOG.error(details);
        } else {
            LOG.error(details, exception.getCause());
        }
    }

    record Result(Path jar, Map<String, byte[]> replacements) {
    }

    private record PreparedPatch(boolean hasInputs, List<ModSpec> enabledMods, Path microforgeRoot,
                                 Path gameJarsDir, ApiPatchPaths.Workspace workspace, UnifiedPatch patch,
                                 String hash) {
        private PreparedPatch {
            enabledMods = List.copyOf(enabledMods);
        }

        static PreparedPatch empty(List<ModSpec> enabledMods, String hash) {
            return new PreparedPatch(false, enabledMods, null, null, null, UnifiedPatch.empty(), hash);
        }
    }
}
