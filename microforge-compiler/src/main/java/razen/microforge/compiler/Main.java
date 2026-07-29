package razen.microforge.compiler;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } catch (CompilerException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        }
    }

    public static void run(String[] args) throws Exception {
        Compiler.compile(CompileJob.fromArgs(args));
    }
}
