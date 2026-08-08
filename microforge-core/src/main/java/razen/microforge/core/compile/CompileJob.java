package razen.microforge.core.compile;

import razen.microforge.core.util.StringUtils;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record CompileJob(String name, List<Path> sourceDirs, List<Path> sourceFiles, Path outputDir, String classpath, Path jarOutput,
                         String sourceEncoding, String sourceHash, Map<String, String> manifestAttributes) {
    public static final String DEFAULT_SOURCE_ENCODING = "UTF-8";

    public CompileJob {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        sourceDirs = sourceDirs == null ? List.of() : List.copyOf(sourceDirs);
        sourceFiles = sourceFiles == null ? List.of() : List.copyOf(sourceFiles);
        if (sourceDirs.isEmpty() && sourceFiles.isEmpty()) {
            throw new IllegalArgumentException("sourceDirs and sourceFiles must not both be empty");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir must not be null");
        }
        sourceEncoding = normalizeSourceEncoding(sourceEncoding);
        sourceHash = StringUtils.cleanString(sourceHash);
        manifestAttributes = manifestAttributes == null ? Map.of() : Map.copyOf(manifestAttributes);
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
