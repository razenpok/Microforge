package razen.microforge;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import razen.microforge.compiler.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class EnabledMods {
    private static final String MOD_ID = "razen_microforge";

    private EnabledMods() {
    }

    static void ensureMicroforgeEnabled() throws IOException {
        var modsPath = StringUtils.cleanString(System.getProperty("com.fs.starfarer.settings.paths.mods"));
        var enabledModsPath = Path.of(modsPath).resolve("enabled_mods.json").normalize();
        var enabledMods = readJson(enabledModsPath);
        var enabledModIds = enabledMods.optJSONArray("enabledMods");
        try {
            if (enabledModIds == null) {
                enabledModIds = new JSONArray();
                enabledMods.put("enabledMods", enabledModIds);
            }

            var reorderedModIds = new JSONArray();
            reorderedModIds.put(MOD_ID);
            for (var i = 0; i < enabledModIds.length(); i++) {
                var modId = StringUtils.cleanString(enabledModIds.optString(i, null));
                if (modId == null || MOD_ID.equals(modId)) {
                    continue;
                }
                reorderedModIds.put(modId);
            }

            enabledMods.put("enabledMods", reorderedModIds);
            Files.writeString(enabledModsPath, enabledMods.toString(2) + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IllegalStateException("Could not update " + enabledModsPath, e);
        }
    }

    private static JSONObject readJson(Path path) throws IOException {
        try {
            if (!Files.exists(path)) {
                var json = new JSONObject();
                json.put("enabledMods", new JSONArray());
                return json;
            }
            return new JSONObject(Files.readString(path, StandardCharsets.UTF_8));
        } catch (JSONException e) {
            throw new IllegalStateException("invalid JSON in " + path, e);
        }
    }
}
