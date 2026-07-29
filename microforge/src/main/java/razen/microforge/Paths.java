package razen.microforge;

import com.fs.starfarer.api.Global;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
                .filter(x -> Objects.equals(x.getId(), "razen_microforge"))
                .findFirst();
        if (mod.isEmpty()) {
            throw new IllegalStateException("No razen_microforge mod found!");
        }

        return modPath = Path.of(mod.get().getPath()).normalize();
    }

    private static Path jarsDir;
    public static Path getGameJarsDir() {
        if (jarsDir != null) {
            return jarsDir;
        }
        var location = Global.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation();

        try {
            var starfarerApiJar = new File(location.toURI());
            return jarsDir = Path.of(starfarerApiJar.getAbsolutePath()).getParent().normalize();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path ecjJar;
    public static Path getEcjJar() throws IOException {
        if (ecjJar != null) {
            return ecjJar;
        }

        var jarsDir = getModPath().resolve("jars");
        try (var jars = Files.list(jarsDir)) {
            return ecjJar = jars.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("ecj"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new IllegalStateException("No ECJ jar found in " + jarsDir))
                    .normalize();
        }
    }
}
