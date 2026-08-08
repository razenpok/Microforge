package razen.microforge.core.mods;

import org.json.JSONException;
import org.json.JSONObject;
import razen.microforge.core.io.StarsectorJSONReader;
import razen.microforge.core.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class ModDescriptorReader {
    private static final String DESCRIPTOR_FILE = "mod_info.json";

    private ModDescriptorReader() {
    }

    public static ModSpec read(Path modDirectory) throws IOException, JSONException {
        var descriptor = descriptorPath(modDirectory);
        var json = StarsectorJSONReader.read(descriptor);
        var dependencies = new ArrayList<String>();
        var dependencyVersions = new LinkedHashMap<String, SemanticVersion>();
        var dependencyArray = json.optJSONArray("dependencies");
        if (dependencyArray != null) {
            for (var i = 0; i < dependencyArray.length(); i++) {
                var dependency = dependencyArray.getJSONObject(i);
                var dependencyId = requiredString(dependency, "id", descriptor);
                dependencies.add(dependencyId);
                var dependencyVersion = version(dependency, "version");
                if (dependencyVersion != null && dependencyVersion.isSet()) {
                    dependencyVersions.putIfAbsent(dependencyId, dependencyVersion);
                }
            }
        }

        var jars = new ArrayList<String>();
        var jarArray = json.optJSONArray("jars");
        if (jarArray != null) {
            for (var i = 0; i < jarArray.length(); i++) {
                var jar = StringUtils.cleanString(jarArray.getString(i));
                if (jar != null) {
                    jars.add(jar);
                }
            }
        }

        return new ModSpec(
                requiredString(json, "id", descriptor),
                requiredString(json, "name", descriptor),
                modDirectory,
                dependencies,
                jars,
                version(json, "version"),
                dependencyVersions);
    }

    static Path descriptorPath(Path modDirectory) {
        return modDirectory.resolve(DESCRIPTOR_FILE);
    }

    private static String requiredString(JSONObject json, String key, Path source) throws JSONException {
        var value = StringUtils.cleanString(json.getString(key));
        if (value == null) {
            throw new JSONException("Missing or empty '" + key + "' in " + source);
        }
        return value;
    }

    private static SemanticVersion version(JSONObject descriptor, String key) throws JSONException {
        var value = descriptor.opt(key);
        if (value instanceof JSONObject version) {
            return SemanticVersion.of(
                    version.getString("major"),
                    version.optString("minor", ""),
                    version.optString("patch", ""));
        }
        return value == null || JSONObject.NULL.equals(value)
                ? null
                : SemanticVersion.parse(String.valueOf(value));
    }
}
