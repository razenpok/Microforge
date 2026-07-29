package razen.microforge.cli;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: cli.sh <command>");
            System.err.println("commands: build");
            System.exit(2);
        }

        var command = args[0];
        if (command.equals("build")) {
            BuildCommand.fromEnvironment().run();
            return;
        }

        System.err.println("error: unknown command '" + command + "'");
        System.err.println("commands: build");
        System.exit(2);
    }
}
