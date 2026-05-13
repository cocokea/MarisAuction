MarisAuction 1.0 final

Final pass changes:
- bumped plugin version to 1.0
- changed plugin.yml dependency order to depend on Vault, NBTAPI, PacketEvents
- adjusted main GUI labels and popup titles closer to the provided screenshots
- kept sign input placeholders for search and price flows
- made Your Items lore show time-left on owned listings
- made transaction stats total across the full history instead of the current page only
- switched sign block state creation to StateTypes.OAK_SIGN.createBlockData() for PacketEvents state creation
- added safer item return handling when backing out of confirm listing
- cleaned message punctuation and added a dropped-item fallback message when inventory is full during an immediate return

Important:
- This container still cannot run a real Gradle build because gradle-wrapper.jar is not present.
- The code was manually re-reviewed to reduce compile risk, but it was not validated with an actual gradlew build inside this environment.
