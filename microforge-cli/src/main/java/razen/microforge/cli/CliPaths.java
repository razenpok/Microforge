package razen.microforge.cli;

import razen.microforge.core.util.StringUtils;

import java.nio.file.Path;

final class CliPaths {
    private CliPaths() {
    }

    static Path requiredProperty(String name) throws BuildException {
        var value = StringUtils.cleanString(System.getProperty(name));
        if (value == null) {
            throw new BuildException("-D" + name + " was not provided");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
