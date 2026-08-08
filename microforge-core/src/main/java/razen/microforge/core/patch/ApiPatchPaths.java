package razen.microforge.core.patch;

import java.nio.file.Path;

public final class ApiPatchPaths {
    public static final String API_JAR_NAME = "starfarer.api.jar";
    public static final String PATCH_NAME = "starfarer.api";
    public static final String PATCH_FILE_NAME = API_JAR_NAME + ".patch";
    public static final Path PATCHES_PATH = Path.of("patches");

    private ApiPatchPaths() {
    }

    public static Path modPatchRoot(Path modRoot) {
        return modRoot.resolve(PATCHES_PATH).resolve(PATCH_NAME);
    }

    public static Path liveSources(Path modRoot) {
        return modPatchRoot(modRoot).resolve("src");
    }

    public static Path outputRoot(Path microforgeRoot) {
        return microforgeRoot.resolve("out").resolve(PATCHES_PATH).resolve(PATCH_NAME);
    }

    public static Workspace workspace(Path microforgeRoot, Path gameJarsDir) {
        var outputRoot = outputRoot(microforgeRoot);
        return new Workspace(
                gameJarsDir.resolve("starfarer.api.zip"),
                outputRoot.resolve("src"),
                outputRoot.resolve("patched"),
                outputRoot.resolve("classes"),
                outputRoot.resolve(PATCH_FILE_NAME),
                outputRoot.resolve(API_JAR_NAME));
    }

    public record Workspace(Path sourceZip, Path sourceRoot, Path patchedRoot, Path classesRoot,
                            Path mergedPatch, Path replacementJar) {
    }
}
