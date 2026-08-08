package razen.microforge.cli;

import org.apache.log4j.BasicConfigurator;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.patch.GameVersion;
import razen.microforge.core.patch.GameVersionMarker;
import razen.microforge.core.mods.ModManager;

import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class PatchCommandTest {
    private PatchCommandTest() {
    }

    public static void main(String[] args) throws Exception {
        BasicConfigurator.configure();
        var temp = Files.createTempDirectory("microforge-cli-patch-");
        try {
            var mods = temp.resolve("mods");
            var microforge = mods.resolve("Microforge");
            var target = mods.resolve("Example");
            var gameLib = temp.resolve("game");
            Files.createDirectories(microforge);
            Files.createDirectories(target);
            Files.createDirectories(gameLib);
            Files.writeString(target.resolve("mod_info.json"),
                    "{\"id\":\"example\",\"name\":\"Example\"}\n", StandardCharsets.UTF_8);
            System.setProperty("com.fs.starfarer.settings.paths.mods", mods.toString());
            require(ModManager.getInstance().getAvailableMod("example") != null,
                    "mods-path property did not let the shared mod manager find the target mod");

            try (var zip = new ZipOutputStream(Files.newOutputStream(gameLib.resolve("starfarer.api.zip")))) {
                zip.putNextEntry(new ZipEntry("test/Api.java"));
                zip.write("package test;\nclass Api { String value = \"€1\"; }\n"
                        .getBytes(Charset.forName("windows-1252")));
                zip.closeEntry();
            }

            var command = new PatchCommand(microforge, gameLib, GameVersion.parse("0.98a-RC8"));
            command.prepare("example");
            var working = target.resolve("patches/starfarer.api/src");
            var outputRoot = microforge.resolve("out/patches/starfarer.api");
            require(GameVersionMarker.isCurrent(outputRoot, GameVersion.parse("0.98a-RC8")),
                    "prepare did not write the output cache game version");
            require(!Files.exists(target.resolve("patches/starfarer.api/.src.prepare.tmp")),
                    "prepare left staging files in the target mod");
            require(!Files.exists(microforge.resolve("out/patches/starfarer.api/.src.prepare.tmp")),
                    "prepare did not clean up its output staging tree");
            require(Files.readString(working.resolve(".gitignore")).equals("*\n"),
                    "prepare did not create the expected .gitignore");
            require(GameVersionMarker.isCurrent(working, GameVersion.parse("0.98a-RC8")),
                    "prepare did not write the current game version");
            require(Files.readString(working.resolve("test/Api.java"), StandardCharsets.UTF_8).contains("€1"),
                    "prepare did not normalize CP1252 sources to UTF-8");
            Files.writeString(working.resolve("test/Api.java"),
                    "package test;\nclass Api { String value = \"€2\"; }\n", StandardCharsets.UTF_8);

            GameVersionMarker.write(working, GameVersion.parse("0.98a-RC7"));
            try {
                command.apply("example");
                throw new AssertionError("apply accepted sources prepared for a different game version");
            } catch (BuildException expected) {
                require(expected.getMessage().contains("do not match Starsector"),
                        "unexpected prepared-source version error: " + expected.getMessage());
            }
            GameVersionMarker.write(working, GameVersion.parse("0.98a-RC8"));
            var staleOutput = outputRoot.resolve("stale-cache");
            Files.writeString(staleOutput, "stale", StandardCharsets.UTF_8);
            GameVersionMarker.write(outputRoot, GameVersion.parse("0.98a-RC7"));
            command.apply("example");
            require(GameVersionMarker.isCurrent(outputRoot, GameVersion.parse("0.98a-RC8")),
                    "apply did not update the output cache game version");
            require(!Files.exists(staleOutput), "apply did not clear a stale output cache");
            var patch = target.resolve("patches/starfarer.api/0.98a-RC8.patch");
            require(Files.readString(patch).contains("€2"), "apply did not capture the UTF-8 source edit");

            FileOperations.deleteRecursively(working);
            command.prepare("example");
            require(Files.readString(working.resolve("test/Api.java"), StandardCharsets.UTF_8).contains("€2"),
                    "prepare did not apply the existing patch");

            FileOperations.deleteRecursively(working);
            Files.delete(patch);
            Files.writeString(patch.resolveSibling("0.98a-RC7.patch"), """
                    diff --git a/test/Api.java b/test/Api.java
                    --- a/test/Api.java
                    +++ b/test/Api.java
                    @@ -1,1 +1,1 @@
                    -this line does not exist
                    +class Api {}
                    """, StandardCharsets.UTF_8);
            command.prepare("example");
            require(Files.readString(working.resolve("test/Api.java"), StandardCharsets.UTF_8).contains("€1"),
                    "prepare did not fall back to pristine sources after an incompatible patch");
            System.out.println("PatchCommandTest passed");
        } finally {
            FileOperations.deleteRecursively(temp);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
