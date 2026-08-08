# Modder guide

Run the cross-platform `cli.cmd` launcher from the Microforge mod folder. On
Windows, use `cli.cmd`; on macOS and Linux, use `./cli.cmd`. The launcher
automatically forwards the command to the script for your platform, so you do
not need to invoke `cli.sh` or `cli.ps1` directly.

## Preparing starfarer.api sources

Open the Microforge mod folder in your terminal, then prepare your mod's
sources:

```text
# Windows
cli.cmd prepare your_mod_id

# macOS/Linux
./cli.cmd prepare your_mod_id
```

This command creates `{your_mod}/patches/starfarer.api/src`, which contains the
sources from `starfarer.api.jar`. You can edit the files in this folder
directly, and Microforge will automatically rebuild them when the game starts.
From here, you can start experimenting with the game code.

You **should not** include `{your_mod}/patches/starfarer.api/src` folder in the
release .zip of your mod. Instead, read how to use patches in the next section.

## Applying your changes to a patch

Once you are done with your changes, run `cli.cmd apply {mod_id}` on Windows or
`./cli.cmd apply {mod_id}` on macOS/Linux from the Microforge mod folder. This
command gathers your changes into a small `.patch` file named with the current
version of the game, for example,
`{your_mod}/patches/starfarer.api/0.98a-RC5.patch`. Do not move or rename this
file. This patch file **should** go in your release `.zip`.

When running the `prepare` command, Microforge first looks for a patch matching
the current game version, so you do not need to apply your existing changes
manually. If none exists, it tries to apply the newest patch for an earlier game
version.

## Patch compatibility with different game versions

Even on a non-matching game version, Microforge will still try to apply the
latest patch available. If the code around your patch did not change too much,
there is a good chance it will still work, and you will not need to do anything.
Otherwise, you will need to go through `prepare` -> change `src` -> `apply`
again. Keep your previous patches if you want to support older versions of the
game.

## Microforge mod dependency

Declare a compatible Microforge version in your `mod_info.json`. The installed
version must have the same major version and an equal or newer minor version;
patch versions do not affect compatibility.

```json
{
  "dependencies": [
    {
      "id": "razen_microforge",
      "name": "Microforge",
      "version": "1.0.0"
    }
  ]
}
```

If Microforge detects an incompatible version declaration, it disables the
affected mod, displays an explanation, and continues to the launcher.
