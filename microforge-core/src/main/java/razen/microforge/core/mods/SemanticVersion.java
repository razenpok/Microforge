package razen.microforge.core.mods;

import java.math.BigInteger;
import java.util.Objects;

/**
 * A version parsed using Starsector's mod version rules.
 * Reference: <a href="https://fractalsoftworks.com/forum/index.php?topic=4760.0">Mod descriptor</a>
 **/
public final class SemanticVersion {
    private final String major;
    private final String minor;
    private final String patch;
    private final String text;

    private SemanticVersion(String major, String minor, String patch, String text) {
        this.major = Objects.requireNonNull(major, "major");
        this.minor = Objects.requireNonNull(minor, "minor");
        this.patch = Objects.requireNonNull(patch, "patch");
        this.text = Objects.requireNonNull(text, "text");
    }

    public static SemanticVersion parse(String value) {
        var text = Objects.requireNonNull(value, "version");
        var parts = text.split("\\.|a-RC|a");
        var major = "";
        var minor = "";
        var patch = "";
        if (parts.length == 2) {
            if (parts[0].equals("0")) {
                major = parts[0] + "." + parts[1];
            } else {
                major = parts[0];
                minor = parts[1];
            }
        } else if (parts.length == 3) {
            if (parts[0].equals("0")) {
                major = parts[0] + "." + parts[1];
                minor = parts[2];
            } else {
                major = parts[0];
                minor = parts[1];
                patch = parts[2];
            }
        } else if (parts.length == 4) {
            major = parts[0] + "." + parts[1];
            minor = parts[2];
            patch = parts[3];
        }
        return new SemanticVersion(major, minor, patch, text);
    }

    public static SemanticVersion of(String major, String minor, String patch) {
        var cleanMajor = Objects.requireNonNull(major, "major");
        var cleanMinor = minor == null ? "" : minor;
        var cleanPatch = patch == null ? "" : patch;
        return new SemanticVersion(cleanMajor, cleanMinor, cleanPatch,
                format(cleanMajor, cleanMinor, cleanPatch));
    }

    public String major() {
        return major;
    }

    public String minor() {
        return minor;
    }

    public String patch() {
        return patch;
    }

    public boolean isSet() {
        return !major.isEmpty();
    }

    public boolean satisfiesMajorMinor(SemanticVersion required) {
        if (!required.isSet()) {
            return true;
        }
        if (!isSet() || !major.equals(required.major)) {
            return false;
        }
        if (minor.equals(required.minor) || required.minor.isEmpty()) {
            return true;
        }
        var installedMinor = integer(minor);
        var requiredMinor = integer(required.minor);
        return installedMinor != null && requiredMinor != null
                && installedMinor.compareTo(requiredMinor) >= 0;
    }

    private static BigInteger integer(String value) {
        if (value.isEmpty()) {
            return null;
        }
        for (var i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') {
                return null;
            }
        }
        return new BigInteger(value);
    }

    private static String format(String major, String minor, String patch) {
        if (patch.isEmpty() && minor.isEmpty()) {
            return major;
        }
        if (patch.isEmpty()) {
            return major + "." + minor;
        }
        return major + "." + minor + "." + patch;
    }

    @Override
    public String toString() {
        return text;
    }
}
