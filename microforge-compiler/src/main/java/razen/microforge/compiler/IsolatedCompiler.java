package razen.microforge.compiler;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Compiler that gets loaded in a separate classloader.
 */
public final class IsolatedCompiler implements AutoCloseable {
    private final URLClassLoader loader;
    private final Method run;

    public IsolatedCompiler(Path ecjJar) {
        URL[] urls;
        try {
            urls = new URL[]{ ecjJar.toUri().toURL(), ownCodeSourceLocation() };
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }

        loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
        try {
            var compilerMain = Class.forName(Main.class.getName(), true, loader);
            run = compilerMain.getMethod("run", String[].class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalStateException("could not load " + Main.class.getName(), e);
        }
    }

    public Output run(CompileJob job) throws InvocationTargetException {
        var originalOut = System.out;
        var originalErr = System.err;
        var stdout = new ProxyOutputStream(originalOut);
        var stderr = new ProxyOutputStream(originalErr);
        var capturingOut = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        var capturingErr = new PrintStream(stderr, true, StandardCharsets.UTF_8);

        try {
            System.setOut(capturingOut);
            System.setErr(capturingErr);
            run.invoke(null, (Object) job.toArgs());
            capturingOut.flush();
            capturingErr.flush();
            return new Output(stdout.text(), stderr.text());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InvocationTargetException e) {
            capturingOut.flush();
            capturingErr.flush();
            throw new InvocationFailure(e.getCause(), new Output(stdout.text(), stderr.text()));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }

    private static URL ownCodeSourceLocation() {
        try {
            return IsolatedCompiler.class.getProtectionDomain().getCodeSource().getLocation();
        } catch (RuntimeException e) {
            throw new IllegalStateException("could not resolve this class's own code source location", e);
        }
    }

    public record Output(String stdout, String stderr) {
    }

    public static final class InvocationFailure extends InvocationTargetException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Output output;

        private InvocationFailure(Throwable cause, Output output) {
            super(cause);
            this.output = output;
        }

        public Output output() {
            return output;
        }
    }

    private static final class ProxyOutputStream extends OutputStream {
        private final PrintStream delegate;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        private ProxyOutputStream(PrintStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void write(int b) {
            delegate.write(b);
            captured.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            delegate.write(b, off, len);
            captured.write(b, off, len);
        }

        @Override
        public synchronized void flush() {
            delegate.flush();
        }

        private synchronized String text() {
            return captured.toString(StandardCharsets.UTF_8);
        }
    }
}
