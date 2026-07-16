# StorageLens

StorageLens gives Hypixel SkyBlock players one profile-aware place to find
items across live and cached inventories.

![StorageLens Item Search](https://cdn.modrinth.com/data/YfhLlJko/images/8d13308d510c92869fc78cf377f7835c79b48d78.png)

## Search every known location

- Inventory, armor, and equipped equipment
- Ender Chest, backpacks, and Rift Storage
- Loadouts, Wardrobe, and Equipment Sets
- Accessory Bag, sacks, Sack of Sacks, and Personal Vault
- Forge, Museum, and installed tool parts
- Legitimately opened private-island chests

Exact item variants stay separate, while matching copies are aggregated with
their total amount and every known location. Results can be filtered by source
and sorted by amount, value, rarity, or name.

## Safe navigation

StorageLens can open safe destination menus or highlight an inventory slot or
island chest. It never retrieves items, clicks slots, walks to chests, or opens
containers automatically.

When an item exists in several island chests, the blurred location picker can
highlight one chest or all of them. Double chests receive one combined outline.
The highlight lasts 10 seconds by default and is configurable from 1 to 60
seconds.

## Profile isolation

Local observations are separated by Minecraft account and exact SkyBlock
profile name. Switching profiles immediately cancels search work, closes the
screen, and clears pending highlights. Unknown-profile sessions expose only
live data and do not write caches.

Island chests are recorded only after they are legitimately opened on the
player's own private island. StorageLens never scans chunks or reads unopened
block entities.

## Configuration

Press `I` on SkyBlock or run `/storagelens search`. The keybind is available in
Minecraft Controls and StorageLens settings. The MoulConfig screen includes
SkyHUD's Original and Transparent themes, the same default navy accent, search
field toggles, stale warnings, source toggles, optional island warping, and
profile-scoped clear actions.

## Compatibility

- Minecraft 26.1.2 and 26.2
- Fabric client-side
- Fabric Loader 0.19.3 or newer
- Java 25

MoulConfig and SkyblockAPI 4.2.10 are embedded. A separate SkyblockAPI or
Hypixel Mod API installation is not required.
