package razen.microforge;

import com.fs.starfarer.api.ModSpecAPI;
import org.json.JSONException;
import org.json.JSONObject;
import razen.microforge.compiler.CompileJob;
import razen.microforge.compiler.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class MicroforgeConfig {
    private static final Path CONFIG_PATH = Path.of("microforge.config.json");
    private static final Path DEFAULT_SOURCE_DIR = Path.of("src");

    private final Path modRoot;
    private final JSONObject config;

    private MicroforgeConfig(Path modRoot, JSONObject config) {
        this.modRoot = modRoot;
        this.config = config;
    }

    static Optional<MicroforgeConfig> read(Path modRoot) throws IOException {
        var configPath = modRoot.resolve(CONFIG_PATH).normalize();
        if (!Files.isRegularFile(configPath)) {
            return Optional.empty();
        }
        return Optional.of(new MicroforgeConfig(modRoot.normalize(), readJson(configPath)));
    }

    boolean isBuildEnabled() {
        return buildBoolean("enabled", false);
    }

    Path sourceDir() {
        return resolvePath(buildString("src"), DEFAULT_SOURCE_DIR);
    }

    Path jarOutput(ModSpecAPI mod) {
        var jarOutput = buildString("jarOutput");
        if (jarOutput == null) {
            jarOutput = firstJarEntry(mod);
        }
        if (jarOutput == null) {
            throw new IllegalStateException("no build.jarOutput and no mod_info.json jars entry for " + mod.getId());
        }
        return resolveJarPath(this.modRoot, jarOutput);
    }

    String sourceEncoding() {
        var sourceEncoding = buildString("sourceEncoding");
        if (sourceEncoding == null) {
            sourceEncoding = buildString("encoding");
        }
        return CompileJob.normalizeSourceEncoding(sourceEncoding);
    }

    static Path resolveJarPath(Path modRoot, String jarPath) {
        var path = Path.of(jarPath);
        if (path.isAbsolute()) {
            throw new IllegalArgumentException("jar path must be relative to the mod directory: " + jarPath);
        }
        return modRoot.resolve(path).normalize();
    }

    private Path resolvePath(String configured, Path defaultRelativePath) {
        if (configured == null) {
            return this.modRoot.resolve(defaultRelativePath).normalize();
        }

        var path = Path.of(configured);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return this.modRoot.resolve(path).normalize();
    }

    private String firstJarEntry(ModSpecAPI mod) {
        var jars = mod.getJars();
        if (jars != null && !jars.isEmpty()) {
            return StringUtils.cleanString(jars.get(0));
        }

        return null;
    }

    private boolean buildBoolean(String name, boolean defaultValue) {
        var build = this.config.optJSONObject("build");
        if (build != null && build.has(name)) {
            return build.optBoolean(name, defaultValue);
        }
        return this.config.optBoolean("build." + name, defaultValue);
    }

    private String buildString(String name) {
        var build = this.config.optJSONObject("build");
        if (build != null) {
            var value = StringUtils.cleanString(build.optString(name, null));
            if (value != null) {
                return value;
            }
        }
        return StringUtils.cleanString(this.config.optString("build." + name, null));
    }

    private static JSONObject readJson(Path path) throws IOException {
        try {
            return new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IllegalStateException("invalid JSON in " + path, e);
        }
    }
}
