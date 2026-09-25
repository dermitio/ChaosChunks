# ChaosChunks

Custom world generation mod for Minecraft (NeoForge).

---

## Features

* Custom biome selection system
* Dimension-specific biome control
* Chaos-style biome generation
* Configurable through world creation UI
* Biome tag and blacklist support

---

## Installation

1. Install **Minecraft 26.3**
2. Install **NeoForge 26.3.0.7-beta or newer for Minecraft 26.3**
3. Download the latest ChaosChunks.jar from Curseforge
- https://www.curseforge.com/minecraft/mc-mods/chaoschunks
4. Place it in your `mods/` folder

---

## Development Setup

```bash
git clone https://github.com/YOURNAME/ChaosChunks.git
cd ChaosChunks
./gradlew runClient
```

Requires:

* Java 25 (Gradle selects its Java 25 build JVM automatically)
* NeoForge MDK environment

---

## Usage

1. Open the world creation screen
2. Select **ChaosChunks** in world types and click **Customize**
3. Select **Global** or a dimension from the scrollable list.
4. On **Biomes**, search biome IDs or `#tags` and toggle **Whitelist** or **Blacklist**. Click a selected rule again to clear it. **Show text** exposes the editable four-group expression; edits stay synchronized with the table.
5. Dimensions inherit Global rules until you edit them. Restore inheritance under **Settings → Biome rules**. Settings also contains generation mode, region seed randomization, and experimental terrain profiles. Global Settings contains the region sizes.
6. Return to the dimension list and click **Done** to save, or **Cancel** to discard the draft. Then generate the world.

* When entering biome tags or ids or blacklisting use the following format:
### [],[],[],[] / "","","",""
* Each bracket/quote has its functionality tied to its order

Format:

```
[positive biome tags],[positive biome ids],[negative biome ids],[negative biome tags]
```

Explanation:
- First: biome tags to include
- Second: biome ids to include
- Third: biome ids to exclude
- Fourth: biome tags to exclude

*Example:*

```
### the_nether = [#minecraft:is_overworld],[minecraft:the_void],[minecraft:swamp,minecraft:river],[#minecraft:is_forest]
```

* This will create the nether with only regular overworld biomes without the forests, swamp and river biomes and the void biome added in

---

## Compatibility

* Minecraft: **26.3**
* Loader: **NeoForge**
* Works in singleplayer and servers
* Server-side only — clients can join without installing the mod

Known issues:

* Biomes without features are usually handled, but edge cases may still occur

---

## License

This project is licensed under the **MIT License**.
See the `LICENSE` file for details.

---

## Contributing

Pull requests, forks, and experiments are welcome.
If you build something cool with this mod, feel free to share it.
If you backport the mod (please do) be proud of it.

---

## Credits

* NeoForge team
* Mojang
* Anyone who helped test or report issues
* My sanity about biome features

---

## Future Plans

* *Hopefully nothing.*
* Smooth biomes
* Updates to new minecraft versions

---

## Notes

The mod is not exactly lightweight as it turns chunk generation into a benchmark for the CPU.
Also this mod was created with the idea of use alongside of world type to biome mods so go give those a try
