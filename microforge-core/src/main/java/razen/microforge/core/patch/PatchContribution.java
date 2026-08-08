package razen.microforge.core.patch;

public record PatchContribution(String modName, UnifiedPatch patch) {
    public PatchContribution {
        if (modName == null || modName.isBlank()) {
            throw new IllegalArgumentException("modName must not be blank");
        }
        patch = patch == null ? UnifiedPatch.empty() : patch;
    }
}
