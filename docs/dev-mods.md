# Mods in `fabric/dev-mods`

`fabric/build.gradle` adds every jar in `fabric/dev-mods` to the dev client at runtime only (`localRuntime`), so the
expansion can be tried with other mods. Nothing here is a compile dependency. To set up a new machine, copy the folder,
or download the versions below for Minecraft 26.2 (Fabric). The jars are not in git.

## Our own mods (built from sibling repos)

| Jar | Mod id | Repo | What it does |
|---|---|---|---|
| `grounded_villages-fabric-0.1.0+26.2.jar` | `grounded_villages` | `../grounded-villages` | Village houses fitted to the terrain, with flattening and smoothing |
| `tabletop-fabric-0.1.0+26.2.jar` | `tabletop` | `../tabletop` | Put any item down on a block, drawn in 3D with its item model |
| `village_furnishings-fabric-0.1.0+26.2.jar` | `village_furnishings` | `../village-furnishings` | Swinging shutters, casement windows, and iron/copper/gold sinks |

To rebuild one: run `./gradlew :fabric:build` in its repo and copy `fabric/build/libs/<mod>-fabric-*.jar` here.
Only do this with the dev client closed.

## Mods MCA integrates with (through data files)

| Jar | Mod id | Version | Link | Used for |
|---|---|---|---|---|
| `FarmersDelight-26.2-3.6.29+refabricated.jar` | `farmersdelight` | 26.2-3.6.29 | https://www.curseforge.com/minecraft/mc-mods/farmers-delight-refabricated | Cooking and food opportunities |
| `storagedelight-26.07.01-26.2-fabric.jar` | `storagedelight` | 26.07.01 | https://github.com/axperty/storagedelight | Storage containers villagers use |
| `beautify-fabric-3.0.0+26.2.jar` | `beautify` | 3.0.0 | https://www.curseforge.com/minecraft/mc-mods/beautify-refabricated | Blinds and lamps (daily toggles), decoration |
| `immersive_melodies-fabric-0.8.2+26.2.jar` | `immersive_melodies` | 0.8.2 | https://conczin.net/ | Villagers playing instruments |
| `tables-chairs-2-0.6.jar` | `mr_tables_chairs2` | 0.6 | https://modrinth.com/datapack/tables-chairs-2 | Chairs and tables for sitting and meals |
| `ItemsDisplayed_-_v1.3-26.2.jar` | `items_displayed` | 1.3 | https://www.curseforge.com/minecraft/mc-mods/items-displayed | Compared against Tabletop (kept for testing) |
| `ToughAsNails-fabric-26.2-21.11.0.8.jar` | `toughasnails` | 21.11.0.8 | https://www.curseforge.com/minecraft/mc-mods/tough-as-nails | Thirst and temperature next to MCA's needs |

## World generation and villages

| Jar | Mod id | Version | Link |
|---|---|---|---|
| `bettervillage-fabric-26.2-4.0.1.jar` | `bettervillage` | 4.0.1 | https://github.com/jtorleon-studios-team/better-villages/ |
| `improved-village-placement-1.2.0-fabric-26.2.jar` | `ivp` | 1.2.0 | https://modrinth.com/project/improved-village-placement |
| `tectonic-3.0.28-fabric-26.2.jar` | `tectonic` | 3.0.28 | https://modrinth.com/project/tectonic |
| `lithostitched-1.8.0-fabric-26.2.jar` | `lithostitched` | 1.8.0 | https://modrinth.com/project/lithostitched |
| `TerraBlender-fabric-26.2-26.2.0.0.2.jar` | `terrablender` | 26.2.0.0.2 | https://www.curseforge.com/minecraft/mc-mods/terrablender |

## Visuals and animation

| Jar | Mod id | Version | Link |
|---|---|---|---|
| `AnimatedDoors-2.0.2.jar` | `animated_doors` | 2.0.2 | (from the Downloads folder; no homepage in its metadata) |
| `notenoughanimations-fabric-1.12.5-mc26.2.jar` | `notenoughanimations` | 1.12.5 | https://modrinth.com/mod/not-enough-animations |
| `firstperson-fabric-2.7.3-mc26.2.jar` | `firstperson` | 2.7.3 | https://modrinth.com/mod/first-person-model |

## Libraries other mods here need

| Jar | Mod id | Version | Needed by |
|---|---|---|---|
| `cloth-config-26.2.155.jar` | `cloth-config` | 26.2.155 | Config screens of several mods |
| `GlitchCore-fabric-26.2-26.2.0.0.0.jar` | `glitchcore` | 26.2.0.0.0 | Tough As Nails |
| `libraryferret-fabric-26.2-5.0.1.jar` | `libraryferret` | 5.0.1 | Better Villages |

## Outside this folder

- `fabric/run/mods`: `entity_model_features-3.3.11-26.2-fabric.jar` and `entity_texture_features-7.2.5-26.2-fabric.jar`
  (Fresh Animations, the villager model and the `mca_talk` animation variables need them).
- `fabric/run/resourcepacks`: `FreshAnimations_v1.10.5.zip`.
- `fabric/build.gradle` pulls more mods from Modrinth on its own (`localRuntime "maven.modrinth:..."`): Sodium, JEI,
  Iris, FerriteCore, ImmediatelyFast and Packet Fixer. Nothing to copy for those.

Snapshot taken 2026-10-24.
