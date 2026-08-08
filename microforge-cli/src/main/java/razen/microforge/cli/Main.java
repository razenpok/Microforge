package razen.microforge.cli;

import org.apache.log4j.BasicConfigurator;
import razen.microforge.core.patch.PatchCompatibilityException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("log4j.defaultInitOverride", "true");
        BasicConfigurator.configure();

        if (args.length == 0) {
            printUsage();
            System.exit(2);
        }

        try {
            var command = args[0];
            switch (command) {
                case "build" -> {
                    requireArgumentCount(args, 1);
                    BuildCommand.fromEnvironment().run();
                }
                case "test" -> {
                    requireArgumentCount(args, 1);
                    TestCommand.fromEnvironment().run();
                }
                case "prepare" -> {
                    requireArgumentCount(args, 2);
                    PatchCommand.fromEnvironment().prepare(args[1]);
                }
                case "apply" -> {
                    requireArgumentCount(args, 2);
                    PatchCommand.fromEnvironment().apply(args[1]);
                }
                default -> {
                    System.err.println("error: unknown command '" + command + "'");
                    printUsage();
                    System.exit(2);
                }
            }
        } catch (PatchCompatibilityException e) {
            logDetails(e);
            System.err.println("error: " + cliMessage(e));
            System.exit(1);
        } catch (BuildException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.err.println("error: Microforge CLI command failed.\n"
                    + "Check the details above.");
            System.exit(1);
        }
    }

    private static void logDetails(PatchCompatibilityException exception) {
        if (exception.details() != null && !exception.details().isBlank()) {
            System.err.println("details: " + exception.details());
        }
        if (exception.getCause() != null) {
            exception.getCause().printStackTrace(System.err);
        }
    }

    private static String cliMessage(PatchCompatibilityException exception) {
        return exception.getMessage().replace(
                "Check starsector.log for more info.",
                "Check the details above.");
    }

    private static void requireArgumentCount(String[] args, int expected) {
        if (args.length == expected) {
            return;
        }
        System.err.println("error: command '" + args[0] + "' expects " + (expected - 1) + " argument(s)");
        printUsage();
        System.exit(2);
    }

    private static void printUsage() {
        System.err.println("usage: cli.cmd <command> [arguments]");
        System.err.println("commands:");
        System.err.println("  build");
        System.err.println("  prepare <mod_id>");
        System.err.println("  apply <mod_id>");
        System.err.println("  test");
    }
}
