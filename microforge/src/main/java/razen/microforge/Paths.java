package razen.microforge;

import com.fs.starfarer.api.Global;
import razen.microforge.core.mods.ModManager;
import razen.microforge.core.patch.ApiPatchPaths;
import razen.microforge.core.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class Paths {
    private static Path modPath;
    public static Path getModPath() {
        if (modPath != null) {
            return modPath;
        }
        var mod = Global.getSettings()
                .getModManager()
                .getAvailableModsCopy()
                .stream()
                .filter(x -> Objects.equals(x.getId(), ModManager.MICROFORGE_ID))
                .findFirst();
        if (mod.isEmpty()) {
            throw new IllegalStateException("Microforge was not found in the mod manager");
        }

        return modPath = Path.of(mod.get().getPath()).normalize();
    }

    private static Path jarsDir;
    public static Path getGameJarsDir() {
        if (jarsDir != null) {
            return jarsDir;
        }
        try {
            return jarsDir = findGameJarsDir();
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    static Path findGameJarsDir() throws IOException {
        var workingDir = Path.of(System.getProperty("user.dir", "")).toAbsolutePath().normalize();
        var classpath = StringUtils.cleanString(System.getProperty("java.class.path"));
        if (classpath != null) {
            for (var entry : classpath.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                var cleanEntry = StringUtils.cleanString(entry);
                if (cleanEntry == null) {
                    continue;
                }
                var path = Path.of(cleanEntry);
                if (!path.isAbsolute()) {
                    path = workingDir.resolve(path);
                }
                path = path.normalize();
                if (path.getFileName() != null
                        && path.getFileName().toString().equals(ApiPatchPaths.API_JAR_NAME)
                        && Files.isRegularFile(path)) {
                    return path.getParent();
                }
            }
        }

        if (Files.isRegularFile(workingDir.resolve(ApiPatchPaths.API_JAR_NAME))) {
            return workingDir;
        }
        throw new IOException("could not locate " + ApiPatchPaths.API_JAR_NAME + " on the game classpath");
    }

    private static Path ecjJar;
    public static Path getEcjJar() throws IOException {
        if (ecjJar != null) {
            return ecjJar;
        }

        return ecjJar = findEcjJar(getModPath());
    }

    static Path findEcjJar(Path microforgeRoot) throws IOException {
        var jarsDir = microforgeRoot.resolve("jars");
        try (var jars = Files.list(jarsDir)) {
            return jars.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("ecj"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IOException("no ECJ jar found in " + jarsDir))
                    .normalize();
        }
    }

    public static List<Path> gameClasspathJars() throws IOException {
        try (var files = Files.list(getGameJarsDir())) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
