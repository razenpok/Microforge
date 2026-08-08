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

## Examples

[An assortment of silly things](https://github.com/razenpok/MicroforgeExample):

- Officer personalities affect salary. Timid and cautious officers offer
  discounts, while aggressive and reckless officers demand hazard pay.
- The Impatient Sector. Everything happens twice as often, but the clock ticks
  the same.
- Vanilla hub missions now roll more often. A 1% chance becomes 10%, a 25%
  chance becomes 50%.
- Squared weights of everything. A choice with weight 10 is 100 times as likely
  as a choice with weight 1, instead of 10 times as likely.
- Percentage modifiers compound. Two +50% modifiers turn 100 into 225 rather
  than 200; two -50% modifiers turn 100 into 25 rather than 0. Applies to every
  in-game modifier.
- Hyperspace storms restore CR and hull instead of causing damage.

[Ashes of the Domain industry deficit calculation patch](https://github.com/razenpok/MicroforgeAotDPatch)

## Ad hoc mod compilation

As a fun side effect of having a full Java compiler available at hand,
Microforge also allows on-the-fly jar compilation. Yeah, like Janino, but using
ECJ for broader support for modern Java syntax. To opt in, create the following
microforge.config.json file in the root of your mod:

```
{
  "build": {
    "enabled": true
  }
}
```