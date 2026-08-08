package razen.microforge.core.mods;

import razen.microforge.core.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModJars {
    private ModJars() {
    }

    public static List<Path> existing(ModSpec mod, Path excluded) {
        var modRoot = mod.root();
        var excludedJar = excluded == null ? null : normalize(excluded);
        var result = new ArrayList<Path>();
        for (var configured : mod.jars()) {
            var relativeJar = StringUtils.cleanString(configured);
            if (relativeJar == null) {
                continue;
            }
            var jar = normalize(modRoot.resolve(relativeJar));
            if (jar.startsWith(modRoot) && !jar.equals(excludedJar) && Files.isRegularFile(jar)) {
                result.add(jar);
            }
        }
        return List.copyOf(result);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
