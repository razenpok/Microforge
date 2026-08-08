package razen.microforge.core.modbuild;

import razen.microforge.core.MicroforgeConfig;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.mods.ModSpec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ModBuildPlannerTest {
    private ModBuildPlannerTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("ModBuildPlannerTest passed");
    }

    public static void run() throws Exception {
            var temp = Files.createTempDirectory("microforge-build-plan-");
        try {
            var gameJar = temp.resolve("game.jar");
            var transitiveRoot = temp.resolve("transitive");
            var dependencyRoot = temp.resolve("dependency");
            var targetRoot = temp.resolve("target");
            Files.write(gameJar, new byte[0]);
            createBuildableMod(transitiveRoot, "transitive.jar");
            createBuildableMod(dependencyRoot, "dependency.jar");
            createBuildableMod(targetRoot, "target.jar");

            var transitive = new ModSpec("transitive", "transitive", transitiveRoot, List.of(),
                    List.of("jars/transitive.jar"));
            var dependency = new ModSpec("dependency", "dependency", dependencyRoot, List.of("transitive"),
                    List.of("jars/dependency.jar"));
            var target = new ModSpec("target", "target", targetRoot, List.of("dependency"),
                    List.of("jars/target.jar"));
            var planner = new ModBuildPlanner(temp.resolve("out"), List.of(gameJar),
                    List.of(target, dependency, transitive));
            var plan = planner.plan(planner.findTargets());
            require(plan.orderedTargets().stream().map(ModBuildTarget::modId).toList()
                            .equals(List.of("transitive", "dependency", "target")),
                    "dependencies were not ordered before their consumers");

            var targetBuild = plan.targetsById().get("target");
            var dependencyBuild = plan.targetsById().get("dependency");
            var transitiveBuild = plan.targetsById().get("transitive");
            var classpath = planner.classpathFor(targetBuild, plan.targetsById()).stream()
                    .map(Path::toString)
                    .toList();
            require(classpath.contains(gameJar.toAbsolutePath().normalize().toString()),
                    "game classpath was omitted");
            require(classpath.contains(dependencyBuild.jarOutput().toString()),
                    "dependency build output was omitted");
            require(classpath.contains(transitiveBuild.jarOutput().toString()),
                    "transitive dependency build output was omitted");
            require(!classpath.contains(targetBuild.jarOutput().toString()),
                    "target jar was included on its own compile classpath");

            var cyclicTarget = new ModSpec("target", "target", targetRoot, List.of("dependency"), List.of());
            var cyclicDependency = new ModSpec("dependency", "dependency", dependencyRoot, List.of("target"),
                    List.of());
            var cyclicPlanner = new ModBuildPlanner(temp.resolve("cycle-out"), List.of(gameJar),
                    List.of(cyclicTarget, cyclicDependency));
            try {
                cyclicPlanner.plan(cyclicPlanner.findTargets());
                throw new AssertionError("expected cyclic build dependencies to be rejected");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("cyclic"),
                        "unexpected dependency-cycle error: " + expected.getMessage());
            }

            try {
                MicroforgeConfig.resolveJarPath(targetRoot, "../escaped.jar");
                throw new AssertionError("expected escaping jar output to be rejected");
            } catch (IllegalArgumentException expected) {
                require(expected.getMessage().contains("escapes"),
                        "unexpected jar path error: " + expected.getMessage());
            }
        } finally {
            FileOperations.deleteRecursively(temp);
        }
    }

    private static void createBuildableMod(Path root, String jarName) throws Exception {
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("jars"));
        Files.writeString(root.resolve("src/Example.java"), "class Example {}\n", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("microforge.config.json"),
                "{\"build\":{\"enabled\":true,\"jarOutput\":\"jars/" + jarName + "\"}}\n",
                StandardCharsets.UTF_8);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

}
