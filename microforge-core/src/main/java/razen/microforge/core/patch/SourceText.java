package razen.microforge.core.patch;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SourceText {
    static final Charset STARSECTOR_CHARSET = Charset.forName("windows-1252");

    private SourceText() {
    }

    static Map<String, TextFile> readPristineJavaTree(Path root) throws IOException {
        return readJavaTree(root, null);
    }

    static Map<String, TextFile> readWorkingJavaTree(Path root, Map<String, TextFile> pristine)
            throws IOException {
        return readJavaTree(root, pristine);
    }

    static TextFile readPristineFile(Path path) throws IOException {
        return readTextFile(path, STARSECTOR_CHARSET);
    }

    /** A null pristine map means this is a freshly unpacked, explicitly Windows-1252 source tree. */
    private static Map<String, TextFile> readJavaTree(Path root, Map<String, TextFile> pristine) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("source directory does not exist: " + root);
        }
        var normalizedRoot = root.toAbsolutePath().normalize();
        var result = new LinkedHashMap<String, TextFile>();
        try (var paths = Files.walk(normalizedRoot)) {
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".java"))
                    .sorted(Comparator.comparing(file -> normalizedRoot.relativize(file).toString()))
                    .toList()) {
                var relative = normalizedRoot.relativize(path).toString().replace('\\', '/');
                result.put(relative, pristine == null
                        ? readPristineFile(path)
                        : readWorkingTextFile(path, pristine.get(relative)));
            }
        }
        return result;
    }

    private static TextFile readWorkingTextFile(Path path, TextFile pristine) throws IOException {
        var bytes = Files.readAllBytes(path);
        var windows1252 = textFile(new String(bytes, STARSECTOR_CHARSET));
        TextFile utf8;
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            utf8 = textFile(decoder.decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException e) {
            return windows1252;
        }

        if (utf8.lines().equals(windows1252.lines()) || pristine == null) {
            return utf8;
        }
        var utf8Changes = changedTextSpan(pristine, utf8);
        var windows1252Changes = changedTextSpan(pristine, windows1252);
        return utf8Changes <= windows1252Changes ? utf8 : windows1252;
    }

    private static int changedTextSpan(TextFile beforeFile, TextFile afterFile) {
        var before = beforeFile.text();
        var after = afterFile.text();
        var prefix = 0;
        while (prefix < before.length() && prefix < after.length()
                && before.charAt(prefix) == after.charAt(prefix)) {
            prefix++;
        }
        var beforeEnd = before.length();
        var afterEnd = after.length();
        while (beforeEnd > prefix && afterEnd > prefix
                && before.charAt(beforeEnd - 1) == after.charAt(afterEnd - 1)) {
            beforeEnd--;
            afterEnd--;
        }
        return beforeEnd - prefix + afterEnd - prefix;
    }

    static TextFile readTextFile(Path path, Charset charset) throws IOException {
        return textFile(Files.readString(path, charset));
    }

    static TextFile textFile(String value) {
        var text = normalizeNewlines(value);
        var trailingNewline = text.endsWith("\n");
        var lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        if (trailingNewline && !lines.isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return new TextFile(List.copyOf(lines), trailingNewline);
    }

    static void writeUtf8(Path path, TextFile file) throws IOException {
        var text = String.join("\n", file.lines());
        if (file.trailingNewline()) {
            text += "\n";
        }
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static String normalizeNewlines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    record TextFile(List<String> lines, boolean trailingNewline) {
        static TextFile empty() {
            return new TextFile(List.of(), true);
        }

        String text() {
            return String.join("\n", lines) + (trailingNewline ? "\n" : "");
        }
    }
}
