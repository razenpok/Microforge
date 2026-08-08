package razen.microforge.core.compile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CompileJobBuilder {
    private String name;
    private final List<Path> sourceDirs = new ArrayList<>();
    private final List<Path> sourceFiles = new ArrayList<>();
    private Path outputDir;
    private String classpath;
    private Path jarOutput;
    private String sourceEncoding;
    private String sourceHash;
    private Map<String, String> manifestAttributes = Map.of();

    public CompileJobBuilder name(String name) {
        this.name = name;
        return this;
    }

    public CompileJobBuilder sourceDir(Path sourceDir) {
        sourceDirs.add(sourceDir);
        return this;
    }

    public CompileJobBuilder sourceDirs(List<Path> sourceDirs) {
        this.sourceDirs.clear();
        this.sourceDirs.addAll(sourceDirs);
        return this;
    }

    public CompileJobBuilder sourceFiles(List<Path> sourceFiles) {
        this.sourceFiles.clear();
        this.sourceFiles.addAll(sourceFiles);
        return this;
    }

    public CompileJobBuilder outputDir(Path outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public CompileJobBuilder classpath(String classpath) {
        this.classpath = classpath;
        return this;
    }

    public CompileJobBuilder jarOutput(Path jarOutput) {
        this.jarOutput = jarOutput;
        return this;
    }

    public CompileJobBuilder sourceEncoding(String sourceEncoding) {
        this.sourceEncoding = sourceEncoding;
        return this;
    }

    public CompileJobBuilder sourceHash(String sourceHash) {
        this.sourceHash = sourceHash;
        return this;
    }

    public CompileJobBuilder manifestAttributes(Map<String, String> manifestAttributes) {
        this.manifestAttributes = manifestAttributes;
        return this;
    }

    public CompileJob build() {
        return new CompileJob(name, sourceDirs, sourceFiles, outputDir, classpath, jarOutput, sourceEncoding, sourceHash,
                manifestAttributes);
    }
}
