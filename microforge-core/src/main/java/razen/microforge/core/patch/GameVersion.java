package razen.microforge.core.patch;

import com.fs.starfarer.Version;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record GameVersion(String value) implements Comparable<GameVersion> {
    private static final Pattern VALID_VERSION = Pattern.compile("[0-9][0-9A-Za-z._-]*");
    private static final Pattern TOKEN = Pattern.compile("[0-9]+|[A-Za-z]+");

    public GameVersion {
        if (value == null || !VALID_VERSION.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid game version: " + value);
        }
    }

    public static GameVersion parse(String value) {
        return new GameVersion(value == null ? null : value.trim());
    }

    public static GameVersion load() throws IOException {
        try {
            // Using com.fs.starfarer.Version is kinda safe here because
            // it will not load any additional important classes from game lib.
            return parse(Version.versionInfoForMods.getString());
        } catch (LinkageError | RuntimeException e) {
            throw new IOException("could not read the Starsector game version from Version.versionInfoForMods", e);
        }
    }

    @Override
    public int compareTo(GameVersion other) {
        var left = tokens(value);
        var right = tokens(other.value);
        for (var index = 0; index < Math.min(left.size(), right.size()); index++) {
            var comparison = left.get(index).compareTo(right.get(index));
            if (comparison != 0) {
                return comparison;
            }
        }
        var comparison = Integer.compare(left.size(), right.size());
        if (comparison != 0) {
            return comparison;
        }
        comparison = value.compareToIgnoreCase(other.value);
        return comparison != 0 ? comparison : value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }

    private static List<Token> tokens(String version) {
        var result = new ArrayList<Token>();
        var matcher = TOKEN.matcher(version);
        while (matcher.find()) {
            var token = matcher.group();
            result.add(Character.isDigit(token.charAt(0))
                    ? Token.number(new BigInteger(token))
                    : Token.text(token));
        }
        return List.copyOf(result);
    }

    private record Token(BigInteger number, String text) implements Comparable<Token> {
        static Token number(BigInteger value) {
            return new Token(value, null);
        }

        static Token text(String value) {
            return new Token(null, value);
        }

        @Override
        public int compareTo(Token other) {
            if (number != null && other.number != null) {
                return number.compareTo(other.number);
            }
            if (text != null && other.text != null) {
                return text.compareToIgnoreCase(other.text);
            }
            return number != null ? 1 : -1;
        }
    }
}
