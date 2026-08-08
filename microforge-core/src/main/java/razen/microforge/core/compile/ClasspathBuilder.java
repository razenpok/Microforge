package razen.microforge.core.compile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ClasspathBuilder {
    private final Set<Path> entries = new LinkedHashSet<>();

    public ClasspathBuilder addAll(Collection<Path> paths) {
        paths.stream().map(ClasspathBuilder::normalize).forEach(entries::add);
        return this;
    }

    public ClasspathBuilder addJars(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(ClasspathBuilder::normalize)
                    .forEach(entries::add);
        }
        return this;
    }

    public ClasspathBuilder add(Path path) {
        entries.add(normalize(path));
        return this;
    }

    public String build() {
        return entries.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));
    }

    public List<Path> entries() {
        return List.copyOf(entries);
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
