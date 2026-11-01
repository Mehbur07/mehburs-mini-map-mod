# Mehbur's Mini Map

A client-side 3D minimap mod for Minecraft 26.2 (Fabric). Work in progress.

## Build

Requires JDK 25.

```
./gradlew build
```

The mod jar is written to `build/libs/`.

## Deploy to a test instance

Copy `local.properties.example` to `local.properties`, set `test_instance_dir`, then run:

```
./gradlew deployToTestInstance
```

The instance also needs Fabric API.
