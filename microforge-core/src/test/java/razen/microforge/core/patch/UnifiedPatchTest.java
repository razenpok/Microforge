package razen.microforge.core.patch;

import java.io.IOException;

import static razen.microforge.core.patch.PatchTestSupport.require;

public final class UnifiedPatchTest {
    private UnifiedPatchTest() {
    }

    public static void main(String[] args) throws Exception {
        run();
        System.out.println("UnifiedPatchTest passed");
    }

    public static void run() throws Exception {
        parseCountsOmittedByDiffFormat();
        rejectTruncatedHunk();
        rejectOverlongHunk();
        rejectUnsafePath();
    }

    private static void parseCountsOmittedByDiffFormat() throws Exception {
        var patch = UnifiedPatch.parse("""
                --- a/test/A.java
                +++ b/test/A.java
                @@ -1 +1 @@
                -old
                +new
                """, "count-defaults");
        require(patch.files().size() == 1 && patch.files().get(0).hunks().get(0).lines().size() == 2,
                "single-line default counts were not parsed");
        require(UnifiedPatch.parse(patch.serialize(), "round-trip").equals(patch),
                "serialized patch did not round-trip");
    }

    private static void rejectTruncatedHunk() throws Exception {
        expectInvalid("""
                --- a/test/A.java
                +++ b/test/A.java
                @@ -1,2 +1,2 @@
                -old
                +new
                """, "declare 2 old/2 new lines");
    }

    private static void rejectOverlongHunk() throws Exception {
        expectInvalid("""
                --- a/test/A.java
                +++ b/test/A.java
                @@ -1,1 +1,1 @@
                 unchanged
                +extra
                """, "unexpected content");
    }

    private static void rejectUnsafePath() throws Exception {
        expectInvalid("""
                --- a/../Escaped.java
                +++ b/../Escaped.java
                @@ -1 +1 @@
                -old
                +new
                """, "unsupported patch path");
    }

    private static void expectInvalid(String text, String expectedMessage) throws Exception {
        try {
            UnifiedPatch.parse(text, "malformed-test");
            throw new AssertionError("expected malformed patch to be rejected");
        } catch (IOException expected) {
            require(expected.getMessage().contains(expectedMessage),
                    "unexpected parser error: " + expected.getMessage());
        }
    }
}
