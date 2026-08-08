package razen.microforge.core.patch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class PatchFingerprint {
    private PatchFingerprint() {
    }

    public static String hash(GameVersion gameVersion, UnifiedPatch patch) {
        var digest = sha256();
        update(digest, "microforge-merged-patch-v1\n");
        update(digest, gameVersion == null ? "" : gameVersion.toString());
        update(digest, "\n");
        update(digest, patch == null ? "" : patch.serialize());
        return hex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
