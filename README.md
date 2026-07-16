# StorageLens

StorageLens is an upcoming client-side Fabric mod for Hypixel SkyBlock.

This repository currently contains project, multi-version build, and release
infrastructure only. Mod implementation has not started.

## Supported versions

- Minecraft 26.1.2 + Fabric
- Minecraft 26.2 + Fabric

## Building

Build both supported targets with Java 25:

```bash
./gradlew build
```

Production JARs are written under `versions/*/build/libs`.

## Releases

Pushing a tag that exactly matches `v<mod_version>` builds every configured
target, publishes one version per target to Modrinth, and creates a GitHub
release containing all production JARs.
