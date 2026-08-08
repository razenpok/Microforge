package razen.microforge.core.patch;

import org.apache.log4j.Logger;
import razen.microforge.core.mods.ModSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Discovers and merges live or versioned API patch inputs from enabled mods. */
public final class ApiPatchInputs {
    private static final Logger LOG = Logger.getLogger(ApiPatchInputs.class);

    private final GameVersion gameVersion;
    private final List<Candidate> candidates;

    private ApiPatchInputs(GameVersion gameVersion, List<Candidate> candidates) {
        this.gameVersion = gameVersion;
        this.candidates = List.copyOf(candidates);
    }

    public static ApiPatchInputs discover(List<ModSpec> enabledMods, GameVersion gameVersion) throws IOException {
        var candidates = new ArrayList<Candidate>();
        for (var mod : enabledMods) {
            var patchRoot = ApiPatchPaths.modPatchRoot(mod.root());
            var liveSources = ApiPatchPaths.liveSources(mod.root());
            try {
                if (Files.isDirectory(liveSources)) {
                    validateLiveSources(mod.name(), liveSources, gameVersion);
                    candidates.add(Candidate.live(mod.name(), liveSources));
                    continue;
                }
                VersionedPatchLoader.select(patchRoot, gameVersion, mod.name())
                        .ifPresent(selection -> candidates.add(Candidate.versioned(mod.name(), selection)));
            } catch (PatchCompatibilityException e) {
                throw e;
            } catch (IOException e) {
                throw PatchCompatibility.failure(mod.name(), gameVersion, e);
            }
        }
        return new ApiPatchInputs(gameVersion, candidates);
    }

    public boolean isEmpty() {
        return candidates.isEmpty();
    }

    public UnifiedPatch generatePatch(Path sourceRoot) throws IOException {
        var contributions = new ArrayList<PatchContribution>();
        for (var candidate : candidates) {
            try {
                var patch = loadPatch(sourceRoot, candidate);
                if (!patch.isEmpty()) {
                    contributions.add(new PatchContribution(candidate.modName(), patch));
                }
            } catch (PatchCompatibilityException e) {
                throw e;
            } catch (IOException e) {
                throw PatchCompatibility.failure(candidate.modName(), gameVersion, e);
            }
        }
        try {
            return PatchMerger.merge(sourceRoot, contributions);
        } catch (PatchContributionException e) {
            throw PatchCompatibility.failure(e.modName(), gameVersion, e);
        }
    }

    private UnifiedPatch loadPatch(Path sourceRoot, Candidate candidate) throws IOException {
        if (candidate.liveSources() != null) {
            validateLiveSources(candidate.modName(), candidate.liveSources(), gameVersion);
            LOG.info("Generating live Starsector API patch for " + candidate.modName());
            return SourceDiffer.createDiff(sourceRoot, candidate.liveSources());
        }

        var selection = candidate.selection();
        if (!selection.isExact(gameVersion)) {
            LOG.warn("Mod " + candidate.modName() + " has no Starsector API patch for " + gameVersion
                    + "; validating compatibility patch " + selection.version() + ".");
        }
        var loaded = VersionedPatchLoader.load(sourceRoot, selection, gameVersion, candidate.modName());
        if (loaded.remapped()) {
            LOG.warn("Remapped compatibility patch " + selection.version() + " for mod "
                    + candidate.modName() + " to Starsector " + gameVersion + ".");
        }
        return loaded.patch();
    }

    private static void validateLiveSources(String modName, Path liveSources, GameVersion gameVersion)
            throws IOException {
        if (!GameVersionMarker.isCurrent(liveSources, gameVersion)) {
            throw PatchCompatibility.preparedSourcesMismatch(modName, gameVersion, liveSources);
        }
    }

    private record Candidate(String modName, Path liveSources, VersionedPatchLoader.Selection selection) {
        static Candidate live(String modName, Path sources) {
            return new Candidate(modName, sources, null);
        }

        static Candidate versioned(String modName, VersionedPatchLoader.Selection selection) {
            return new Candidate(modName, null, selection);
        }
    }
}
