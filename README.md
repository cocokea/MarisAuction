# MarisAuction

Project scaffold for a Bukkit/Paper auction plugin built with Gradle Kotlin DSL and Java 25.

## Notes

- The project uses `compileOnly` for VaultAPI, PacketEvents, NBT-API plugin and HikariCP as requested.
- PacketEvents sign input is implemented with `WrapperPlayServerBlockChange`, `WrapperPlayServerOpenSignEditor`, and `WrapperPlayClientUpdateSign`.
- SQLite and MySQL drivers are left as runtime dependencies.
- All SQL access is wrapped through asynchronous `BukkitRunnable` tasks.
- Returned items are now given back through the `Your Items` flow; `/ah claim` has been removed from commands and config.
- `gradle-wrapper.properties` now points to `gradle-9.1.0-bin.zip` instead of the invalid `gradle-9.1-bin.zip` URL.
- This export still does not include `gradle-wrapper.jar` because it is not available in this environment.
