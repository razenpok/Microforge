package razen.microforge.core.modbuild;

import org.apache.log4j.Logger;
import razen.microforge.core.compile.ClasspathBuilder;
import razen.microforge.core.compile.CompilationRunner;
import razen.microforge.core.compile.CompileJobBuilder;
import razen.microforge.core.compile.JarPackager;
import razen.microforge.core.compile.SourceHasher;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.mods.ModSpec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModProcessor {
    private static final Logger LOG = Logger.getLogger(ModProcessor.class);

    private final Path buildOutputRoot;
    private final Path ecjJar;
    private final ModBuildPlanner planner;

    public ModProcessor(Path buildOutputRoot, Path ecjJar, List<Path> gameClasspathJars,
                        List<ModSpec> enabledMods) {
        this.buildOutputRoot = buildOutputRoot.toAbsolutePath().normalize();
        this.ecjJar = ecjJar.toAbsolutePath().normalize();
        this.planner = new ModBuildPlanner(this.buildOutputRoot, gameClasspathJars, enabledMods);
    }

    public List<ModBuildTarget> findTargets() throws IOException {
        return planner.findTargets();
    }

    public void compileAll(List<ModBuildTarget> targets, ProgressListener progress) throws Exception {
        var plan = planner.plan(targets);
        var compiler = new CompilationRunner(LOG);
        try {
            for (var completed = 0; completed < plan.orderedTargets().size(); completed++) {
                var target = plan.orderedTargets().get(completed);
                var classpath = planner.classpathFor(target, plan.targetsById());
                var fingerprintInputs = new ArrayList<>(classpath);
                fingerprintInputs.add(ecjJar);
                var hash = SourceHasher.hashWithInputs(target.sourceDir(), target.sourceEncoding(), fingerprintInputs);
                if (isUpToDate(target, hash)) {
                    LOG.info("Skipping " + target.displayName() + "; build inputs unchanged.");
                } else {
                    progress.onProgress(target, completed, plan.orderedTargets().size());
                    compile(target, compiler, classpath, hash);
                }
                progress.onProgress(null, completed + 1, plan.orderedTargets().size());
            }
        } finally {
            FileOperations.deleteRecursively(buildOutputRoot);
        }
    }

    private void compile(ModBuildTarget target, CompilationRunner compiler, List<Path> classpath, String hash)
            throws Exception {
        FileOperations.deleteRecursively(target.outputDir());
        Files.createDirectories(target.outputDir());
        var job = new CompileJobBuilder()
                .name(target.modId())
                .sourceDir(target.sourceDir())
                .outputDir(target.outputDir())
                .classpath(new ClasspathBuilder().addAll(classpath).build())
                .jarOutput(target.jarOutput())
                .sourceEncoding(target.sourceEncoding())
                .sourceHash(hash)
                .build();
        try {
            compiler.compile(job, target.displayName());
        } finally {
            FileOperations.deleteRecursively(target.outputDir());
        }
    }

    private static boolean isUpToDate(ModBuildTarget target, String hash) {
        try {
            return Files.isRegularFile(target.jarOutput()) && hash.equals(JarPackager.readJarHash(target.jarOutput()));
        } catch (IOException e) {
            LOG.warn("Could not read Microforge source hash from " + target.jarOutput() + "; rebuilding.", e);
            return false;
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(ModBuildTarget target, int completed, int total);
    }
}
