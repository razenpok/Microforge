package razen.microforge.cli;

import razen.microforge.core.patch.PatchApplier;
import razen.microforge.core.patch.ApiPatchPaths;
import razen.microforge.core.patch.PatchCompatibility;
import razen.microforge.core.patch.PatchCompatibilityException;
import razen.microforge.core.patch.SourceDiffer;
import razen.microforge.core.patch.SourceWorkspace;
import razen.microforge.core.patch.GameVersion;
import razen.microforge.core.patch.GameVersionMarker;
import razen.microforge.core.patch.VersionedPatchLoader;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.mods.ModManager;
import razen.microforge.core.mods.ModSpec;
import razen.microforge.core.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.stream.Collectors;

final class PatchCommand {
    private final ModManager modManager;
    private final Path sourceZip;
    private final Path outputRoot;
    private final Path unpackedSource;
    private final GameVersion gameVersion;

    PatchCommand(Path microforgeRoot, Path gameLib, GameVersion gameVersion) {
        this(new ModManager(microforgeRoot.getParent()), microforgeRoot, gameLib, gameVersion);
    }

    private PatchCommand(ModManager modManager, Path microforgeRoot, Path gameLib, GameVersion gameVersion) {
        this.modManager = modManager;
        var workspace = ApiPatchPaths.workspace(microforgeRoot, gameLib);
        this.sourceZip = workspace.sourceZip().toAbsolutePath().normalize();
        this.outputRoot = ApiPatchPaths.outputRoot(microforgeRoot);
        this.unpackedSource = workspace.sourceRoot();
        this.gameVersion = gameVersion;
    }

    static PatchCommand fromEnvironment() throws BuildException {
        var root = Path.of("").toAbsolutePath().normalize();
        var gameLib = CliPaths.requiredProperty("razen.microforge.cli.path.game");
        if (!Files.isDirectory(gameLib)) {
            throw new BuildException("game lib directory not found at " + gameLib);
        }
        if (!Files.isRegularFile(gameLib.resolve("starfarer.api.zip"))) {
            throw new BuildException("starfarer.api.zip not found in " + gameLib);
        }
        try {
            return new PatchCommand(ModManager.getInstance(), root, gameLib, GameVersion.load());
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new BuildException("Could not determine the Starsector game version.\n"
                    + "Check the details above.", e);
        }
    }

    void prepare(String modId) throws IOException, BuildException {
        var mod = findMod(modId);
        var modRoot = mod.root();
        var modName = mod.name();
        var patchRoot = ApiPatchPaths.modPatchRoot(modRoot);
        var workingSources = patchRoot.resolve("src");
        if (Files.exists(workingSources)) {
            throw new BuildException("prepared sources already exist at " + workingSources
                    + "\nRun apply first, then move or delete that directory before preparing again.");
        }

        resetOutputIfStale();
        SourceWorkspace.unpackIfAbsent(sourceZip, unpackedSource);
        var staging = unpackedSource.resolveSibling(".src.prepare.tmp");
        FileOperations.deleteRecursively(staging);
        try {
            Set<Path> patchedSources;
            try {
                patchedSources = applyVersionedPatch(patchRoot, modName);
            } catch (PatchCompatibilityException e) {
                var details = StringUtils.cleanString(e.details());
                System.err.println("warning: " + (details == null ? e.getMessage() : details));
                System.err.println("warning: preparing pristine Starsector API sources instead");
                patchedSources = Set.of();
            }
            materializePreparedTree(staging, patchedSources);
            GameVersionMarker.write(staging, gameVersion);
            FileOperations.copyTree(staging, workingSources);
        } catch (IOException | RuntimeException e) {
            try {
                FileOperations.deleteRecursively(workingSources);
            } catch (IOException cleanupFailure) {
                e.addSuppressed(cleanupFailure);
            }
            throw e;
        } finally {
            FileOperations.deleteRecursively(staging);
        }
        System.out.println("prepared " + modName + " sources at " + workingSources);
    }

    private Set<Path> applyVersionedPatch(Path patchRoot, String modName) throws IOException {
        try {
            var loaded = VersionedPatchLoader.loadBest(patchRoot, unpackedSource, gameVersion, modName);
            if (loaded.isEmpty()) {
                return Set.of();
            }

            var patch = loaded.get();
            if (!patch.isExact(gameVersion)) {
                System.err.println("warning: using " + patch.version()
                        + " compatibility patch for mod '" + modName + "' with Starsector " + gameVersion);
            }
            if (patch.remapped()) {
                System.err.println("warning: remapped shifted patch hunks to the current pristine source");
            }
            return PatchApplier.apply(unpackedSource, patch.patch());
        } catch (PatchCompatibilityException e) {
            throw e;
        } catch (IOException e) {
            throw PatchCompatibility.failure(modName, gameVersion, e);
        }
    }

    private void materializePreparedTree(Path staging, Set<Path> patchedSources) throws IOException {
        var patchedRoot = unpackedSource.resolveSibling("patched");
        FileOperations.copyTree(unpackedSource, staging);
        for (var patchedSource : patchedSources) {
            var target = staging.resolve(patchedRoot.relativize(patchedSource));
            Files.createDirectories(target.getParent());
            Files.copy(patchedSource, target, StandardCopyOption.REPLACE_EXISTING);
        }
        var stagingUtf8Sources = patchedSources.stream()
                .map(path -> staging.resolve(patchedRoot.relativize(path)))
                .collect(Collectors.toSet());
        SourceWorkspace.normalizePreparedTreeToUtf8(staging, stagingUtf8Sources);
        Files.writeString(staging.resolve(".gitignore"), "*\n", StandardCharsets.UTF_8);
    }

    void apply(String modId) throws BuildException {
        var mod = findMod(modId);
        var modRoot = mod.root();
        var patchRoot = ApiPatchPaths.modPatchRoot(modRoot);
        var workingSources = patchRoot.resolve("src");
        if (!Files.isDirectory(workingSources)) {
            throw new BuildException("prepared sources do not exist at " + workingSources
                    + "\nRun prepare for '" + mod.name() + "' first.");
        }
        validatePreparedSources(workingSources, mod.name());

        try {
            resetOutputIfStale();
            SourceWorkspace.unpackIfAbsent(sourceZip, unpackedSource);
            var patch = SourceDiffer.createDiff(unpackedSource, workingSources);
            var patchText = patch.serialize();
            Files.createDirectories(patchRoot);
            var patchFile = patchRoot.resolve(gameVersion + ".patch");
            FileOperations.writeAtomically(patchFile, patchText);
            System.out.println("wrote " + patchFile + " (" + patchText.lines().count() + " lines)");
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new BuildException("Could not generate a Starsector API patch for '" + mod.name() + "'.\n"
                    + "Check the details above.", e);
        }
    }

    private void resetOutputIfStale() throws IOException {
        if (GameVersionMarker.resetIfStale(outputRoot, gameVersion)) {
            System.err.println("warning: cleared stale Starsector API patch cache at " + outputRoot);
        }
    }

    private void validatePreparedSources(Path workingSources, String modName) throws BuildException {
        try {
            if (GameVersionMarker.isCurrent(workingSources, gameVersion)) {
                return;
            }
        } catch (IOException e) {
            throw new BuildException("Could not validate the prepared Starsector API source version for '"
                    + modName + "'.", e);
        }
        throw new BuildException("prepared Starsector API sources for '" + modName
                + "' do not match Starsector " + gameVersion
                + ".\nMove or delete the src directory, then run prepare again.");
    }

    private ModSpec findMod(String requestedId) throws BuildException {
        var cleanId = StringUtils.cleanString(requestedId);
        if (cleanId == null) {
            throw new BuildException("mod_id must not be blank");
        }
        var mod = modManager.getAvailableMod(cleanId);
        if (mod == null) {
            throw new BuildException("no mod matching the supplied id was found");
        }
        return mod;
    }
}
