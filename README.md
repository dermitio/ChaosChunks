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

1. Install **Minecraft 1.21.11**
2. Install the latest **NeoForge**
3. Download the latest ChaosChunks.jar
4. Place it in your `mods/` folder

---

## Development Setup

```bash
git clone https://github.com/YOURNAME/ChaosChunks.git
cd ChaosChunks
./gradlew runClient
```

Requires:

* Java 21+
* NeoForge MDK environment

---

## Usage

1. Open the world creation screen
2. Select **ChaosChunks** in world types and click **Customize**
3. Configure biome rules or dimension settings
4. Generate the world

* When entering biome tags or ids or blacklisting use the following format:
### [],[],[],[] / "","","",""
* Each bracket/quote has its functionality tied to its order

Format:

[positive biome tags],[positive biome ids],[negative biome ids],[negative biome tags]

Explanation:
- First: biome tags to include
- Second: biome ids to include
- Third: biome ids to exclude
- Fourth: biome tags to exclude

*Example:*
### the_nether = [#minecraft:is_overworld],[minecraft:the_void],[minecraft:swamp,minecraft:river],[#minecraft:is_forest]
* This will create the nether with only regular overworld biomes without the forests, swamp and river biomes and the void biome added in

---

## Compatibility

* Minecraft: **1.21.11**
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

