package razen.microforge.core.mods;

import razen.microforge.core.util.StringUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record ModSpec(String id, String name, Path root, List<String> dependencies, List<String> jars,
                      SemanticVersion version, Map<String, SemanticVersion> dependencyVersions) {
    public ModSpec(String id, String name, Path root, List<String> dependencies, List<String> jars) {
        this(id, name, root, dependencies, jars, null, Map.of());
    }

    public ModSpec {
        id = requireText(id, "id");
        name = requireText(name, "name");
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        root = root.toAbsolutePath().normalize();
        dependencies = List.copyOf(dependencies);
        jars = List.copyOf(jars);
        dependencyVersions = Map.copyOf(dependencyVersions);
    }

    private static String requireText(String value, String field) {
        var clean = StringUtils.cleanString(value);
        if (clean == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return clean;
    }
}
