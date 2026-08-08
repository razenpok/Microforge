package razen.microforge;

import org.apache.log4j.Logger;
import razen.microforge.core.compile.ClasspathBuilder;
import razen.microforge.core.compile.CompilationRunner;
import razen.microforge.core.compile.CompileJobBuilder;
import razen.microforge.core.compile.JarPackager;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.mods.ModJars;
import razen.microforge.core.mods.ModSpec;
import razen.microforge.core.patch.ApiPatchPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

final class GameApiPatchCompiler {
    private static final Logger LOG = Logger.getLogger(GameApiPatchCompiler.class);
    private final List<ModSpec> enabledMods;
    private final Path gameJarsDir;

    GameApiPatchCompiler(List<ModSpec> enabledMods, Path gameJarsDir) {
        this.enabledMods = List.copyOf(enabledMods);
        this.gameJarsDir = gameJarsDir;
    }

    void compile(ApiPatchPaths.Workspace workspace, List<Path> changedSources, String patchHash) throws Exception {
        if (changedSources.isEmpty()) {
            throw new IOException("merged patch changes no Java source files");
        }

        FileOperations.deleteRecursively(workspace.classesRoot());
        Files.createDirectories(workspace.classesRoot());
        var classpath = new ClasspathBuilder().addJars(gameJarsDir);
        for (var mod : enabledMods) {
            classpath.addAll(ModJars.existing(mod, null));
        }
        var job = new CompileJobBuilder()
                .name(ApiPatchPaths.API_JAR_NAME)
                .sourceFiles(changedSources)
                .outputDir(workspace.classesRoot())
                .classpath(classpath.build())
                .jarOutput(workspace.replacementJar())
                .sourceEncoding(StandardCharsets.UTF_8.name())
                .sourceHash(patchHash)
                .build();

        LOG.info("Compiling " + changedSources.size() + " patched Starsector API source file(s).");
        try (var ignored = CompilationMessage.show()) {
            new CompilationRunner(LOG).compile(job, ApiPatchPaths.API_JAR_NAME);
        }
    }

    static boolean isUpToDate(Path jar, String patchHash) {
        if (!Files.isRegularFile(jar)) {
            return false;
        }
        try {
            return patchHash.equals(JarPackager.readJarHash(jar));
        } catch (IOException e) {
            LOG.warn("Could not read patch hash from " + jar + "; rebuilding.", e);
            return false;
        }
    }

    static Map<String, byte[]> readReplacements(Path jarPath) throws IOException {
        var replacements = new LinkedHashMap<String, byte[]>();
        try (var jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                var className = entry.getName().substring(0, entry.getName().length() - ".class".length());
                try (var input = jar.getInputStream(entry)) {
                    replacements.put(className, input.readAllBytes());
                }
            }
        }
        return Map.copyOf(replacements);
    }

}
