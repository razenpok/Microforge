package razen.microforge.core.mods;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class MicroforgeVersionCompatibilityTest {
    private MicroforgeVersionCompatibilityTest() {
    }

    public static void main(String[] args) {
        var fourPart = SemanticVersion.parse("0.3.2.1");
        require(fourPart.major().equals("0.3") && fourPart.minor().equals("2")
                        && fourPart.patch().equals("1"),
                "four-part Starsector version was parsed incorrectly");
        var twoPartZero = SemanticVersion.parse("0.01");
        require(twoPartZero.major().equals("0.01") && twoPartZero.minor().isEmpty(),
                "two-part 0.x Starsector version was parsed incorrectly");
        var freeForm = SemanticVersion.parse("custom");
        require(!freeForm.isSet() && freeForm.toString().equals("custom"),
                "free-form version text was not preserved");
        var taggedInstalled = SemanticVersion.of("1", "preview", "anything");
        require(taggedInstalled.satisfiesMajorMinor(SemanticVersion.of("1", "preview", "else")),
                "matching nonnumeric minor versions were rejected");
        require(!taggedInstalled.satisfiesMajorMinor(SemanticVersion.of("1", "candidate", "anything")),
                "different nonnumeric minor versions were ordered arbitrarily");

        var microforge = mod(ModManager.MICROFORGE_ID, "Microforge", "1.2.0", null);
        var newerMinor = mod("newer_minor", "Newer Minor Mod", "2.0.0", "1.3.0");
        var lowerMajor = mod("lower_major", "Lower Major Mod", "2.0.0", "0.9.9");
        var higherMajor = mod("higher_major", "Higher Major Mod", "2.0.0", "2.0.0");
        var sameMinor = mod("same_minor", "Same Minor Mod", "2.0.0", "1.2.999");
        var lowerMinor = mod("lower_minor", "Lower Minor Mod", "2.0.0", "1.1.999");
        var enabled = List.of(newerMinor, lowerMajor, higherMajor, sameMinor, lowerMinor);
        var report = MicroforgeVersionCompatibility.inspect(microforge, enabled);

        require(report.incompatibleMods().stream().map(requirement -> requirement.mod().id()).toList()
                        .equals(List.of("newer_minor", "lower_major", "higher_major")),
                "wrong mods were considered incompatible: " + report.incompatibleMods());
        require(report.compatiblePatchMods(enabled).stream().map(ModSpec::id).toList()
                        .equals(List.of("same_minor", "lower_minor")),
                "incompatible patch mod was not filtered out");

        var unversionedMicroforge = mod(ModManager.MICROFORGE_ID, "Microforge", "custom", null);
        try {
            MicroforgeVersionCompatibility.inspect(unversionedMicroforge, List.of());
            throw new AssertionError("Microforge's own version was accepted without a major component");
        } catch (IllegalStateException expected) {
            require(expected.getMessage().contains("own version does not define a major"),
                    "unexpected own-version error: " + expected.getMessage());
        }

        System.out.println("MicroforgeVersionCompatibilityTest passed");
    }

    private static ModSpec mod(String id, String name, String version, String microforgeVersion) {
        var dependencies = microforgeVersion == null ? List.<String>of() : List.of(ModManager.MICROFORGE_ID);
        var dependencyVersions = microforgeVersion == null
                ? Map.<String, SemanticVersion>of()
                : Map.of(ModManager.MICROFORGE_ID, SemanticVersion.parse(microforgeVersion));
        return new ModSpec(id, name, Path.of(id), dependencies, List.of(),
                SemanticVersion.parse(version), dependencyVersions);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
