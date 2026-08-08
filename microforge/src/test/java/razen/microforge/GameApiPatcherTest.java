package razen.microforge;

import org.apache.log4j.BasicConfigurator;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.mods.ModManager;
import razen.microforge.core.mods.MicroforgeVersionCompatibility;
import razen.microforge.core.patch.ApiPatchInputs;
import razen.microforge.core.patch.GameVersion;
import razen.microforge.core.patch.GameVersionMarker;
import razen.microforge.core.patch.PatchCompatibilityException;
import razen.microforge.core.patch.SourceWorkspace;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class GameApiPatcherTest {
    private GameApiPatcherTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected: <ecj-jar> <game-jars-dir>");
        }
        BasicConfigurator.configure();

        var temp = Files.createTempDirectory("microforge-runtime-patch-");
        try {
            var ecjJar = Path.of(args[0]);
            var gameJars = Path.of(args[1]);
            var mods = temp.resolve("mods");
            var microforge = mods.resolve("Microforge");
            var patchMod = mods.resolve("PatchMod");
            var liveSources = patchMod.resolve("patches/starfarer.api/src");
            var gameVersion = GameVersion.parse("0.98a-RC8");
            Files.createDirectories(microforge.resolve("jars"));
            Files.copy(ecjJar, microforge.resolve("jars/ecj-test.jar"), StandardCopyOption.REPLACE_EXISTING);
            SourceWorkspace.unpack(gameJars.resolve("starfarer.api.zip"), liveSources);
            modifyCompilableSource(liveSources);
            GameVersionMarker.write(liveSources, gameVersion);

            Files.writeString(microforge.resolve("mod_info.json"),
                    "{\"id\":\"razen_microforge\",\"name\":\"Microforge\","
                            + "\"version\":{\"major\":\"1\",\"minor\":\"0\",\"patch\":\"0\"},"
                            + "\"jars\":[]}\n",
                    StandardCharsets.UTF_8);
            Files.writeString(patchMod.resolve("mod_info.json"),
                    patchModDescriptor("1.0.0"),
                    StandardCharsets.UTF_8);
            Files.writeString(mods.resolve("enabled_mods.json"),
                    "{\"enabledMods\":[\"razen_microforge\",\"patch_mod\"]}\n",
                    StandardCharsets.UTF_8);

            var manager = new ModManager(mods);
            var first = GameApiPatcher.build(manager, gameVersion).orElseThrow();
            var outputRoot = microforge.resolve("out/patches/starfarer.api");
            require(GameVersionMarker.isCurrent(outputRoot, gameVersion),
                    "runtime patch cache has no current version marker");
            require(Files.isDirectory(outputRoot.resolve("src")), "materialized src directory is missing");
            require(Files.isDirectory(outputRoot.resolve("patched")), "patched source directory is missing");
            require(Files.isDirectory(outputRoot.resolve("classes")), "compiled classes directory is missing");
            require(Files.isRegularFile(outputRoot.resolve("starfarer.api.jar.patch")),
                    "merged patch is missing");
            require(Files.isRegularFile(first.jar()), "replacement jar is missing");
            require(!first.replacements().isEmpty(), "replacement jar contains no classes");
            require(GameApiPatcher.isPreparedPatchCurrent(manager, gameVersion),
                    "fresh patch build was not considered current");

            var firstModified = Files.getLastModifiedTime(first.jar());
            var second = GameApiPatcher.build(manager, gameVersion).orElseThrow();
            require(firstModified.equals(Files.getLastModifiedTime(second.jar())),
                    "cache hit rewrote the replacement jar");
            require(first.replacements().keySet().equals(second.replacements().keySet()),
                    "cache hit loaded a different replacement set");

            GameVersionMarker.write(liveSources, GameVersion.parse("0.98a-RC7"));
            try {
                ApiPatchInputs.discover(manager.getEnabledMods(), gameVersion);
                throw new AssertionError("game launch accepted sources prepared for a different game version");
            } catch (PatchCompatibilityException expectedFailure) {
                require(expectedFailure.getMessage().contains("Run Microforge CLI prepare again"),
                        "unexpected live-source version error: " + expectedFailure.getMessage());
            }
            GameVersionMarker.write(liveSources, gameVersion);

            Files.writeString(mods.resolve("enabled_mods.json"),
                    "{\"enabledMods\":[\"razen_microforge\"]}\n", StandardCharsets.UTF_8);
            manager.updateList();
            require(!GameApiPatcher.isPreparedPatchCurrent(manager, gameVersion),
                    "changed enabled-mod selection was considered current");

            Files.writeString(patchMod.resolve("mod_info.json"), patchModDescriptor("1.1.0"),
                    StandardCharsets.UTF_8);
            Files.writeString(mods.resolve("enabled_mods.json"),
                    "{\"enabledMods\":[\"razen_microforge\",\"patch_mod\"]}\n",
                    StandardCharsets.UTF_8);
            manager.updateList();
            var versionCompatibility = MicroforgeVersionCompatibility.inspect(
                    manager.getAvailableMod(ModManager.MICROFORGE_ID), manager.getEnabledMods());
            require(versionCompatibility.incompatibleMods().size() == 1,
                    "newer Microforge dependency was not detected");
            manager.disableMods(versionCompatibility.incompatibleModIds());
            require(!manager.isEnabled("patch_mod"), "incompatible mod remained enabled");
            require(GameApiPatcher.build(manager, gameVersion).isEmpty(),
                    "a disabled incompatible mod still contributed a Starsector API patch");
            require(MicroforgeVersionMessages.modsDisabled(versionCompatibility).contains("launcher will continue"),
                    "startup incompatibility message does not explain launcher recovery");
            require(MicroforgeVersionMessages.cannotStart(versionCompatibility).contains("game cannot start"),
                    "loader incompatibility message does not explain the shutdown");

            var expected = new byte[]{1, 2, 3};
            var transformer = new ReplacementTransformer(java.util.Map.of("test/Replacement", expected));
            require(transformer.transform(null, ClassLoader.getSystemClassLoader(), "test/Replacement",
                    null, null, new byte[0]) == expected, "transformer did not return the replacement bytes");
            require(transformer.transform(null, new ClassLoader() { }, "test/Replacement",
                    null, null, new byte[0]) == null, "transformer accepted a non-system classloader");
            require(transformer.transform(null, ClassLoader.getSystemClassLoader(), "test/Replacement",
                    String.class, null, new byte[0]) == null, "transformer accepted a redefinition");

            var siblingCache = microforge.resolve("out/patches/other.api/cache");
            var staleCache = outputRoot.resolve("stale-cache");
            Files.createDirectories(siblingCache);
            Files.writeString(staleCache, "stale", StandardCharsets.UTF_8);
            var nextVersion = GameVersion.parse("0.98a-RC9");
            require(GameApiPatcher.build(manager, nextVersion).isEmpty(),
                    "patch build unexpectedly found inputs after the patch mod was disabled");
            require(GameVersionMarker.isCurrent(outputRoot, nextVersion),
                    "runtime patch cache version was not updated");
            require(!Files.exists(staleCache), "stale API module cache was not deleted");
            require(Files.isDirectory(siblingCache), "a sibling API module cache was deleted");

            System.out.println("GameApiPatcherTest passed with " + second.replacements().size()
                    + " replacement class(es)");
        } finally {
            FileOperations.deleteRecursively(temp);
        }
    }

    private static void modifyCompilableSource(Path sourceRoot) throws Exception {
        try (var sources = Files.walk(sourceRoot)) {
            for (var source : sources.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                var text = Files.readString(source, Charset.forName("windows-1252"));
                if (text.contains("public class ") || text.contains("public abstract class ")
                        || text.contains("public final class ")) {
                    Files.writeString(source, text + "\n// Microforge runtime integration test\n",
                            StandardCharsets.UTF_8);
                    return;
                }
            }
        }
        throw new AssertionError("could not find a compilable Starsector API source");
    }

    private static String patchModDescriptor(String microforgeVersion) {
        return "{\"id\":\"patch_mod\",\"name\":\"Patch Mod\",\"jars\":[],\"dependencies\":[{"
                + "\"id\":\"razen_microforge\",\"name\":\"Microforge\",\"version\":\""
                + microforgeVersion + "\"}]}\n";
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
