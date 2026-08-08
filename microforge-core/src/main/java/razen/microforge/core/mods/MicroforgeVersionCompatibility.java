package razen.microforge.core.mods;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Checks enabled mods' declared Microforge major/minor compatibility. */
public final class MicroforgeVersionCompatibility {
    private MicroforgeVersionCompatibility() {
    }

    public static Report inspect(ModSpec microforge, List<ModSpec> enabledMods) {
        Objects.requireNonNull(microforge, "microforge");
        if (!ModManager.MICROFORGE_ID.equals(microforge.id())) {
            throw new IllegalArgumentException("expected Microforge mod, got " + microforge.id());
        }
        if (microforge.version() == null) {
            throw new IllegalStateException("Microforge has no version in "
                    + ModDescriptorReader.descriptorPath(microforge.root()));
        }
        if (!microforge.version().isSet()) {
            throw new IllegalStateException("Microforge's own version does not define a major component: "
                    + microforge.version());
        }

        var installed = microforge.version();

        var incompatible = enabledMods.stream()
                .filter(mod -> !ModManager.MICROFORGE_ID.equals(mod.id()))
                .map(mod -> requirement(mod, installed))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new Report(installed, incompatible);
    }

    private static Requirement requirement(ModSpec mod, SemanticVersion installed) {
        var required = mod.dependencyVersions().get(ModManager.MICROFORGE_ID);
        if (required == null) {
            return null;
        }
        return installed.satisfiesMajorMinor(required) ? null : new Requirement(mod, required);
    }

    public record Requirement(ModSpec mod, SemanticVersion requiredVersion) {
    }

    public record Report(SemanticVersion installedVersion, List<Requirement> incompatibleMods) {
        public Report {
            incompatibleMods = List.copyOf(incompatibleMods);
        }

        public boolean isCompatible() {
            return incompatibleMods.isEmpty();
        }

        public Set<String> incompatibleModIds() {
            return incompatibleMods.stream()
                    .map(requirement -> requirement.mod().id())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        public List<ModSpec> compatiblePatchMods(List<ModSpec> enabledMods) {
            if (isCompatible()) {
                return List.copyOf(enabledMods);
            }
            var incompatibleIds = incompatibleModIds();
            return enabledMods.stream()
                    .filter(mod -> !incompatibleIds.contains(mod.id()))
                    .toList();
        }
    }
}
