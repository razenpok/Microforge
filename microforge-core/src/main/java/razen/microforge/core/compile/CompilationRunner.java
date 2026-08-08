package razen.microforge.core.compile;

import org.apache.log4j.Logger;
import razen.microforge.core.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class CompilationRunner {
    private final Logger log;

    public CompilationRunner(Logger log) {
        this.log = log;
    }

    public void compile(CompileJob job, String subject) throws Exception {
        CapturedRun result;
        synchronized (CompilationRunner.class) {
            result = runCaptured(job);
        }
        logOutput(result.output(), subject, result.failure() != null);
        if (result.failure() != null) {
            throw rethrow(subject, result.failure());
        }
    }

    private CapturedRun runCaptured(CompileJob job) {
        var originalOut = System.out;
        var originalErr = System.err;
        var stdout = new ByteArrayOutputStream();
        var stderr = new ByteArrayOutputStream();
        var capturingOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        var capturingErr = new PrintStream(stderr, true, StandardCharsets.UTF_8);
        Throwable failure = null;

        try {
            System.setOut(capturingOut);
            System.setErr(capturingErr);
            Compiler.compile(job);
        } catch (Throwable e) {
            failure = e;
        } finally {
            capturingOut.flush();
            capturingErr.flush();
            System.setOut(originalOut);
            System.setErr(originalErr);
            capturingOut.close();
            capturingErr.close();
        }
        return new CapturedRun(output(stdout, stderr), failure);
    }

    private Exception rethrow(String subject, Throwable cause) {
        if (cause instanceof Exception exception) {
            return exception;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        var cleanSubject = StringUtils.cleanString(subject);
        var message = cleanSubject == null ? "compilation failed" : "failed to compile " + cleanSubject;
        return new IllegalStateException(message, cause);
    }

    private void logOutput(Output output, String subject, boolean failed) {
        if (output == null) {
            return;
        }
        logText("stdout", output.stdout(), subject, failed ? LogLevel.ERROR : LogLevel.INFO);
        logText("stderr", output.stderr(), subject, failed ? LogLevel.ERROR : LogLevel.WARN);
    }

    private void logText(String stream, String text, String subject, LogLevel level) {
        if (text == null || text.isBlank()) {
            return;
        }
        var cleanSubject = StringUtils.cleanString(subject);
        var context = cleanSubject == null ? "" : " for " + cleanSubject;
        var message = "Microforge compiler " + stream + context + ":\n" + text.stripTrailing();
        switch (level) {
            case ERROR -> log.error(message);
            case WARN -> log.warn(message);
            case INFO -> log.info(message);
        }
    }

    private static Output output(ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        return new Output(stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private record CapturedRun(Output output, Throwable failure) {
    }

    private record Output(String stdout, String stderr) {
    }

    private enum LogLevel {
        ERROR,
        WARN,
        INFO
    }
}
