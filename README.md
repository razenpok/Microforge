# Microforge

Microforge compiles mod Java sources and Starsector API source patches while the
game starts.

## Guides

- [Installation guide](https://github.com/razenpok/Microforge/blob/main/guides/installation-guide.md)
- [Modder guide](https://github.com/razenpok/Microforge/blob/main/guides/modder-guide.md)

## How it works

At startup, Microforge collects patches from all enabled mods that declare
patches, checks them for conflicts, compiles the changed classes into one cached
replacement jar, and then starts Starsector. Compatible edits (even separate
edits to the same source file) can coexist. Conflicting edits stop startup with
an error identifying the affected mods.

To compile the jar file, Microforge doesn't use Janino, but instead it ships
with Eclipse compiler for Java
([ECJ](https://github.com/eclipse-jdt/eclipse.jdt.core/tree/master/org.eclipse.jdt.core.compiler.batch))
which is a rock-solid and highly regarded compiler that can deal with modern
Java syntax.

Compilation is cached, so unchanged patches are not recompiled on later
launches. Microforge rebuilds only when the patch set changes.

## Why?

- Source-level patches instead of laborious handwritten (or generated) ASM
- Compatible patches from multiple mods can coexist
- No replacement or modification of the original game jar
- Clear errors when two mods make conflicting changes
- Patches can often continue working across minor game updates
