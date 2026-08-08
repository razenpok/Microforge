package razen.microforge.core.mods;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import razen.microforge.core.io.FileOperations;
import razen.microforge.core.io.StarsectorJSONReader;
import razen.microforge.core.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Manages Starsector's mod descriptors and enabled selection without initializing the game. */
public final class ModManager {
    public static final String MICROFORGE_ID = "razen_microforge";

    private static final Logger LOG = Logger.getLogger(ModManager.class);
    private static final String MODS_PATH_PROPERTY = "com.fs.starfarer.settings.paths.mods";
    private static final String ENABLED_MODS_FILE = "enabled_mods.json";
    private static final String ENABLED_MODS_KEY = "enabledMods";

    private static ModManager instance;

    private final Path modsDirectory;
    private Map<String, ModSpec> availableMods = Map.of();
    private List<ModSpec> enabledMods = List.of();
    private Set<String> enabledModIds = Set.of();

    public static synchronized ModManager getInstance() {
        if (instance == null) {
            var modsPath = StringUtils.cleanString(System.getProperty(MODS_PATH_PROPERTY));
            if (modsPath == null) {
                throw new IllegalStateException("Missing system property " + MODS_PATH_PROPERTY);
            }
            instance = new ModManager(Path.of(modsPath));
        }
        return instance;
    }

    public ModManager(Path modsDirectory) {
        this.modsDirectory = modsDirectory.toAbsolutePath().normalize();
        updateList();
    }

    public synchronized void updateList() {
        if (!Files.isDirectory(modsDirectory)) {
            throw new IllegalStateException("Mod location is not a directory: " + modsDirectory);
        }

        var loadedMods = Map.copyOf(loadAvailableMods());
        var loadedEnabledMods = loadEnabledMods(loadedMods);
        availableMods = loadedMods;
        enabledMods = List.copyOf(loadedEnabledMods);
        enabledModIds = loadedEnabledMods.stream()
                .map(ModSpec::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public synchronized List<ModSpec> getEnabledMods() {
        return enabledMods;
    }

    public synchronized ModSpec getAvailableMod(String modId) {
        var cleanId = StringUtils.cleanString(modId);
        return cleanId == null ? null : availableMods.get(cleanId);
    }

    public synchronized boolean isEnabled(String modId) {
        var cleanId = StringUtils.cleanString(modId);
        return cleanId != null && enabledModIds.contains(cleanId);
    }

    public synchronized void disableMods(Set<String> modIds) throws IOException, JSONException {
        var idsToDisable = modIds.stream()
                .map(StringUtils::cleanString)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (idsToDisable.isEmpty()) {
            return;
        }

        var path = modsDirectory.resolve(ENABLED_MODS_FILE);
        var json = StarsectorJSONReader.read(path);
        var enabledIds = json.optJSONArray(ENABLED_MODS_KEY);
        if (enabledIds == null) {
            return;
        }
        var retainedIds = new JSONArray();
        var changed = false;
        for (var i = 0; i < enabledIds.length(); i++) {
            var id = enabledIds.getString(i);
            if (idsToDisable.contains(StringUtils.cleanString(id))) {
                changed = true;
            } else {
                retainedIds.put(id);
            }
        }
        if (!changed) {
            return;
        }

        json.put(ENABLED_MODS_KEY, retainedIds);
        FileOperations.writeAtomically(path, json.toString(2) + "\n");
        updateList();
    }

    private Map<String, ModSpec> loadAvailableMods() {
        var modsById = new LinkedHashMap<String, ModSpec>();
        try (var entries = Files.list(modsDirectory)) {
            for (var modDirectory : entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList()) {
                var descriptor = ModDescriptorReader.descriptorPath(modDirectory);
                if (!Files.isRegularFile(descriptor)) {
                    continue;
                }
                try {
                    var mod = ModDescriptorReader.read(modDirectory);
                    var existing = modsById.putIfAbsent(mod.id(), mod);
                    if (existing != null) {
                        LOG.warn("Ignoring duplicate mod id '" + mod.id() + "' from " + modDirectory);
                        continue;
                    }
                    LOG.info("Found mod: " + mod.id() + " [" + modDirectory + "]");
                } catch (Exception e) {
                    LOG.error("Error loading mod descriptor " + descriptor, e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not list mod directory " + modsDirectory, e);
        }
        return modsById;
    }

    private List<ModSpec> loadEnabledMods(Map<String, ModSpec> available) {
        var path = modsDirectory.resolve(ENABLED_MODS_FILE);
        if (!Files.isRegularFile(path)) {
            return List.of();
        }

        try {
            var enabledIds = StarsectorJSONReader.read(path).optJSONArray(ENABLED_MODS_KEY);
            if (enabledIds == null) {
                return List.of();
            }
            var result = new ArrayList<ModSpec>();
            var seenIds = new HashSet<String>();
            for (var i = 0; i < enabledIds.length(); i++) {
                var id = StringUtils.cleanString(enabledIds.getString(i));
                if (id == null || !seenIds.add(id)) {
                    continue;
                }
                var mod = available.get(id);
                if (mod != null) {
                    result.add(mod);
                }
            }
            return result;
        } catch (IOException | JSONException e) {
            throw new IllegalStateException("Could not load enabled mod list " + path, e);
        }
    }
}
