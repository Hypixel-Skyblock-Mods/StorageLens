# StorageLens

StorageLens is a client-side Fabric mod for Hypixel SkyBlock that finds items
across the active profile and shows every known location in one responsive
interface.

## Features

- Press `I` while on SkyBlock, or run `/storagelens search [query]`, to open a
  standalone finder without a player inventory panel.
- Search live and cached inventory, armor, equipped equipment, Ender Chest,
  backpacks, Rift Storage, Loadouts, Wardrobe, Equipment Sets, Accessory Bag,
  sacks, Sack of Sacks, Personal Vault, Forge, Museum, installed tool parts,
  and legitimately opened private-island chests.
- Aggregate exact item variants while preserving every known location, long
  sack amounts, cached value estimates, source age, and rarity.
- Search clean names with optional lore, SkyBlock IDs, source names, and
  location names.
- Sort by amount, value, rarity, or name and filter results by source category.
- Open safe destination menus or highlight inventory slots and island chests.
  StorageLens never retrieves, moves, clicks, walks to, or opens an item.
- Choose one location from the blurred location picker, or use **Show All** to
  highlight every distinct island chest containing the item. Double chests use
  one combined outline.
- Configure highlight duration from 1 to 60 seconds. The default is 10 seconds.

## Profile-aware data

StorageLens stores local observations under
`config/storagelens/<account>/<profile>/`. Data is isolated by the exact
SkyBlock profile name. When the profile is unknown, only live data is shown and
nothing is persisted.

Island chest tracking starts only after the player legitimately right-clicks a
normal or trapped chest on their own private island. StorageLens never scans
chunks or reads unopened block entities. If either half of a tracked double
chest is observed breaking, the whole cached container is removed.

Profile changes immediately cancel search work, close the search screen, clear
pending highlights, and prevent data from bleeding between profiles.

## Settings and commands

Run `/storagelens` or use Mod Menu's Config button. StorageLens uses the same
Original and Transparent themes and the same default navy accent as SkyHUD.
The Item Search section contains the synchronized Minecraft keybind, search
field toggles, stale warnings, optional island warping, highlight duration,
individual source toggles, and profile-scoped clear actions.

- `/storagelens` opens settings.
- `/storagelens search` opens Item Search.
- `/storagelens search <query>` opens Item Search with a query.
- `/storagelens search reset-island-chests` clears only the active profile's
  StorageLens island chest observations.

## Supported versions

- Minecraft 26.1.2 + Fabric
- Minecraft 26.2 + Fabric

StorageLens requires Fabric Loader 0.19.3 or newer, Fabric API, Fabric Language
Kotlin, and Java 25. MoulConfig and
[SkyblockAPI](https://github.com/SkyblockAPI/SkyblockAPI) 4.2.10 are embedded in
each JAR together with their required runtime libraries. Players do not need to
install SkyblockAPI or Hypixel Mod API separately.

## Building

Build and test both supported targets:

```bash
./gradlew clean build releaseManifest
```

Production JARs are written to:

```text
versions/mc26_1_2/build/libs/StorageLens-0.1.0+mc26.1.2.jar
versions/mc26_2/build/libs/StorageLens-0.1.0+mc26.2.jar
```

## Releases

Pushing a tag that exactly matches `v<mod_version>` builds every configured
target on the project-specific self-hosted runner pool, publishes one Modrinth
version per Minecraft target, and creates one GitHub release with both JARs.

StorageLens is behaviorally informed by
[SkyOcean's item finder](https://github.com/meowdding/SkyOcean), but its UI,
assets, and implementation are independent. It does not depend on SkyOcean.

## Embedded libraries

SkyblockAPI is distributed under the MIT License. Its complete license notice
is packaged in every StorageLens JAR at
`META-INF/licenses/skyblock-api/LICENSE.txt`.
