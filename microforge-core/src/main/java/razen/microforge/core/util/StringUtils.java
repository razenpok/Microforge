package razen.microforge.core.util;

public final class StringUtils {
    private StringUtils() {
    }

    public static String cleanString(String value) {
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }
}
