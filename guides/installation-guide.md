# Installation guide

First, [download the latest release](https://github.com/razenpok/Microforge/releases/latest/download/Microforge.zip)
and extract it to your `mods` folder like with any other mod. Then, follow the
guide for your OS.

## Windows

In your `vmparams` file, add
`-javaagent:..\mods\Microforge\jars\microforge.jar`.

For example, your `vmparams` might start like this:

```
java.exe -noverify -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions ...
```

Change it to this:

```
java.exe -javaagent:..\mods\Microforge\jars\microforge.jar -noverify -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions ...
```

The `vmparams` file is located in the same folder as `starsector.exe`. If you
play
with [Fast Rendering](https://fractalsoftworks.com/forum/index.php?topic=33870.0),
then you will also need to add Microforge java agent to
`starsector-core/fs.vmparams` file:

```
-javaagent:fr.agent.jar
-javaagent:..\mods\Microforge\jars\microforge.jar
```

## macOS

In your `Starsector.app/Contents/MacOS/starsector_mac.sh` file, add
`-javaagent:../../../mods/Microforge/jars/microforge.jar \` line:

```bash
export JAVA_HOME=../../Home
"$JAVA_HOME/bin/java" \
    -javaagent:../../../mods/Microforge/jars/microforge.jar \
    -Xdock:name="Starsector" \
    -Xdock:icon=../../Resources/s_icon128.icns \
    # ...
```

## Linux

In your `starsector.sh` file, add the
`-javaagent:./mods/Microforge/jars/microforge.jar \` line:

```bash
./jre_linux/bin/java \
    -javaagent:./mods/Microforge/jars/microforge.jar \
    -Dfile.encoding=UTF-8 \
	-noverify \
	# ...
```

## Uninstallation guide

To remove Microforge:

1. Disable it in the mod list
2. Remove the mod folder from `mods`
3. Remove the `-javaagent` that you added during the installation guide
