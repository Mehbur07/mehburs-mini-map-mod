# Mehbur's Mini Map

A client-side 3D minimap mod for Minecraft 26.2 (Fabric): real block terrain drawn with the GPU, color-coded dots for mobs, players, items and projectiles, an animated full-screen map, automatic cave and Nether views, and death markers. English and Turkish.

## Install

1. Use Minecraft 26.2 with Fabric Loader 0.19.3 or newer and Java 25.
2. Put these in your `mods` folder:
   - `mehburs-mini-map-<version>.jar`
   - [Fabric API](https://modrinth.com/mod/fabric-api)
3. Optional, for the settings screen: [Mod Menu](https://modrinth.com/mod/modmenu) and [Cloth Config](https://modrinth.com/mod/cloth-config). Without them the map works normally, and settings can still be edited in `config/mehburs_minimap.json`.

The mod only runs on the client. Servers do not need it, and it sends them nothing of its own.

## Controls

| Key | Action |
|---|---|
| `M` | Open or close the big map (mouse wheel zooms) |
| `K` | Toggle the line to your last death |
| `Shift` + `K` | Remove the death marker until your next death |

All three can be changed in Controls. Both death keys share one key on purpose, and Shift tells them apart. If you give them different keys, Shift no longer matters.

With Iris installed the death keys default to `J`, because Iris toggles shaders on `K`. A key already saved in `options.txt` stays as it is, so if you add Iris to an existing setup, change the two death keys in Controls yourself.

## Settings

Open them through **Mods → Mehbur's Mini Map → settings icon**. Changes apply when saved.

The buttons at the top left of the big map turn the FPS and coordinates lines on and off. They change the same settings as this screen, so they also apply to the small map.

## Compatibility

Tested on Minecraft 26.2 with OpenGL:

| Mod | Version |
|---|---|
| Sodium | 0.9.2 |
| Iris | 1.11.4, with and without the Complementary Reimagined r5.9.3 shader pack |
| ImmediatelyFast | 1.16.5 |

## On servers

- The map only knows what the server sends. Dots disappear beyond the server's entity tracking range, and terrain beyond the chunks it has sent stays empty.
- Servers that hide debug information (the `reduced_debug_info` game rule) also hide the coordinates line.
- Death markers are saved per server address under `config/mehburs_minimap/deaths`. Joining the same server through another address starts empty.

Map terrain is kept in memory only, up to a size limit, and nothing is written to disk except settings and death markers.

## Build

Requires JDK 25.

```
./gradlew build
```

The mod jar is written to `build/libs/`.

## Test

```
./gradlew test
./gradlew runClientGameTest
```

The client gametests open a real game window and write screenshots to `build/run/clientGameTest/screenshots`.

## Deploy to a test instance

Copy `local.properties.example` to `local.properties`, set `test_instance_dir`, then run:

```
./gradlew deployToTestInstance
```

The instance also needs Fabric API.
