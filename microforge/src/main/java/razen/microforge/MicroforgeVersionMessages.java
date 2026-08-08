package razen.microforge;

import razen.microforge.core.mods.MicroforgeVersionCompatibility;

final class MicroforgeVersionMessages {
    private MicroforgeVersionMessages() {
    }

    static String modsDisabled(MicroforgeVersionCompatibility.Report report) {
        return "Microforge disabled these mods because they declare an incompatible Microforge version:\n\n"
                + requirements(report)
                + "\n\nThe launcher will continue with these mods disabled. Update Microforge before re-enabling them.";
    }

    static String cannotStart(MicroforgeVersionCompatibility.Report report) {
        return "The game cannot start because these enabled mods declare an incompatible Microforge version:\n\n"
                + requirements(report)
                + "\n\nMicroforge disabled them. Restart the game, or update Microforge before re-enabling them.";
    }

    private static String requirements(MicroforgeVersionCompatibility.Report report) {
        var lines = new StringBuilder();
        for (var requirement : report.incompatibleMods()) {
            if (!lines.isEmpty()) {
                lines.append('\n');
            }
            lines.append("- ").append(requirement.mod().name())
                    .append(" declares ").append(requirement.requiredVersion())
                    .append("; installed: ").append(report.installedVersion());
        }
        return lines.toString();
    }
}
