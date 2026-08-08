package razen.microforge.core.io;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Reads Starsector JSON files, including their optional BOM and hash comments. */
public final class StarsectorJSONReader {
    private StarsectorJSONReader() {
    }

    public static JSONObject read(Path path) throws IOException, JSONException {
        var text = Files.readString(path, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\ufeff') {
            text = text.substring(1);
        }
        return new JSONObject(stripHashComments(text));
    }

    private static String stripHashComments(String input) {
        var output = new StringBuilder(input.length());
        var inString = false;
        var escaped = false;
        var inComment = false;

        for (var i = 0; i < input.length(); i++) {
            var character = input.charAt(i);
            if (inComment) {
                if (character == '\n' || character == '\r') {
                    inComment = false;
                    output.append(character);
                }
                continue;
            }
            if (inString) {
                output.append(character);
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
                output.append(character);
            } else if (character == '#') {
                inComment = true;
            } else {
                output.append(character);
            }
        }
        return output.toString();
    }
}
