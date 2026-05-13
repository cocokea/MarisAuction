MarisAuction RC2

What changed:
- Reworked build.gradle.kts to use the Spigot snapshots repository and target spigot-api 26.1.2-R0.1-SNAPSHOT.
- Added claim stash logic with a new claims table.
- Added /ah claim command.
- Added expired-listing sweeper that moves expired unsold items into claims.
- Added cancel listing flow from Your Items GUI.
- Added seller-offline-safe payment path using Vault OfflinePlayer deposit.
- Expanded message and gui config keys.
- Polished sort/filter popup layout closer to the provided screenshots.
- Fixed config file regeneration when parent folders are missing.

Notes:
- plugin.yml libraries auto-download is a Paper feature. The plugin still targets Spigot/Bukkit API, but automatic library download requires a Paper-compatible runtime.
- PacketEvents and NBTAPI are expected to be installed as separate plugins.
- This archive still does not include gradle-wrapper.jar, so run `gradle wrapper` once on a development machine if needed.
