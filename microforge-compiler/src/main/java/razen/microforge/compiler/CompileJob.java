package razen.microforge.compiler;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CompileJob(String name, List<Path> sourceDirs, Path outputDir, String classpath, Path jarOutput,
                         String sourceEncoding, String sourceHash, Map<String, String> manifestAttributes) {
    public static final String DEFAULT_SOURCE_ENCODING = "UTF-8";

    public CompileJob {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (sourceDirs == null || sourceDirs.isEmpty()) {
            throw new IllegalArgumentException("sourceDirs must not be empty");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }
        sourceEncoding = normalizeSourceEncoding(sourceEncoding);
        sourceHash = StringUtils.cleanString(sourceHash);
        manifestAttributes = manifestAttributes == null ? Map.of() : Map.copyOf(manifestAttributes);
    }

    public String[] toArgs() {
        var args = new ArrayList<String>();
        args.add("--name");
        args.add(name);
        for (var sourceDir : sourceDirs) {
            args.add("--source");
            args.add(sourceDir.toString());
        }
        args.add("--output");
        args.add(outputDir.toString());
        if (classpath != null && !classpath.isEmpty()) {
            args.add("--classpath");
            args.add(classpath);
        }
        if (jarOutput != null) {
            args.add("--jar");
            args.add(jarOutput.toString());
        }
        args.add("--source-encoding");
        args.add(sourceEncoding);
        if (sourceHash != null) {
            args.add("--source-hash");
            args.add(sourceHash);
        }
        return args.toArray(new String[0]);
    }

    public static CompileJob fromArgs(String[] args) {
        var builder = new CompileJobBuilder();

        var i = 0;
        while (i < args.length) {
            var flag = args[i];
            var value = requireValue(flag, args, i);
            switch (flag) {
                case "--name" -> builder.name(value);
                case "--source" -> builder.sourceDir(Path.of(value));
                case "--output" -> builder.outputDir(Path.of(value));
                case "--classpath" -> builder.classpath(value);
                case "--jar" -> builder.jarOutput(Path.of(value));
                case "--source-encoding", "--encoding" -> builder.sourceEncoding(value);
                case "--source-hash" -> builder.sourceHash(value);
                default -> throw new IllegalArgumentException("unknown flag '" + flag + "'");
            }
            i += 2;
        }

        return builder.build();
    }

    private static String requireValue(String flag, String[] args, int index) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index + 1];
    }

    public static String normalizeSourceEncoding(String sourceEncoding) {
        var clean = StringUtils.cleanString(sourceEncoding);
        if (clean == null) {
            return DEFAULT_SOURCE_ENCODING;
        }

        try {
            return Charset.forName(clean).name();
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            throw new IllegalArgumentException("unsupported source encoding '" + clean + "'", e);
        }
    }
}
