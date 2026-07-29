package razen.microforge.compiler;

import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * This class requires ECJ on the classpath - callers that must keep ECJ off their own classpath
 * should not import this class directly; use IsolatedCompiler instead.
 */
public final class Compiler {
    private Compiler() {
    }

    public static void compile(CompileJob job) throws IOException, CompilerException {
        var kotlinSources = findSourcesWithExtension(job.sourceDirs(), ".kt");
        if (!kotlinSources.isEmpty()) {
            throw new CompilerException("Microforge only supports Java sources, but " + job.name()
                    + " contains " + kotlinSources.size() + " Kotlin source file(s);");
        }

        var sources = findSourcesWithExtension(job.sourceDirs(), ".java");
        if (sources.isEmpty()) {
            throw new CompilerException("no Java sources found under " + job.sourceDirs());
        }

        Files.createDirectories(job.outputDir());
        var args = new ArrayList<>(List.of("-17", "-g:lines,vars,source", "-encoding", job.sourceEncoding(),
                "-proc:none", "-nowarn", "-time"));
        var classpath = job.classpath();
        if (classpath != null && !classpath.isEmpty()) {
            args.add("-cp");
            args.add(classpath);
        }
        args.add("-d");
        args.add(job.outputDir().toString());
        for (var source : sources) {
            args.add(source.toString());
        }

        var success = Main.compile(args.toArray(new String[0]),
                new PrintWriter(System.out, true),
                new PrintWriter(System.err, true),
                null);
        if (!success) {
            throw new CompilerException("compilation of " + job.name() + " failed");
        }

        if (job.jarOutput() != null) {
            var sourceHash = job.sourceHash() != null ? job.sourceHash()
                    : SourceHasher.hash(job.sourceDirs(), job.sourceEncoding());
            var manifestAttributes = new LinkedHashMap<>(job.manifestAttributes());
            manifestAttributes.put(JarPackager.SOURCE_HASH_ATTRIBUTE, sourceHash);
            JarPackager.pack(job.jarOutput(), job.outputDir(), manifestAttributes);
        }
    }

    private static List<Path> findSourcesWithExtension(List<Path> sourceDirs, String extension) throws IOException {
        var sources = new ArrayList<Path>();
        for (var sourceDir : sourceDirs) {
            try (var files = Files.walk(sourceDir)) {
                files.filter(x -> x.toString().endsWith(extension)).forEach(sources::add);
            }
        }
        sources.sort(Comparator.comparing(Path::toString));
        return sources;
    }
}
