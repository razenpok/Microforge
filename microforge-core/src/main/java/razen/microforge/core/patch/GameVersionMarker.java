package razen.microforge.core.patch;

import razen.microforge.core.io.FileOperations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GameVersionMarker {
    private static final String FILE_NAME = "version";

    private GameVersionMarker() {
    }

    public static boolean isCurrent(Path root, GameVersion gameVersion) throws IOException {
        var marker = root.resolve(FILE_NAME);
        return Files.isRegularFile(marker)
                && Files.readString(marker, StandardCharsets.UTF_8).strip().equals(gameVersion.toString());
    }

    public static boolean resetIfStale(Path root, GameVersion gameVersion) throws IOException {
        if (isCurrent(root, gameVersion)) {
            return false;
        }
        var existed = Files.exists(root);
        FileOperations.deleteRecursively(root);
        write(root, gameVersion);
        return existed;
    }

    public static void write(Path root, GameVersion gameVersion) throws IOException {
        FileOperations.writeAtomically(root.resolve(FILE_NAME), gameVersion + "\n");
    }
}
