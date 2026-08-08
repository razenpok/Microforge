package razen.microforge;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Map;

final class ReplacementTransformer implements ClassFileTransformer {
    private final Map<String, byte[]> replacements;

    ReplacementTransformer(Map<String, byte[]> replacements) {
        this.replacements = Map.copyOf(replacements);
    }

    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain domain,
            byte[] originalBytes
    ) {
        if (classBeingRedefined != null) {
            return null;
        }
        if (loader != ClassLoader.getSystemClassLoader()) {
            return null;
        }
        return replacements.get(className);
    }
}
