# MarisAuction

MarisAuction is a GUI auction house plugin with multilingual resources and configurable sounds.

## What It Handles

- Auction browsing and search
- Selling items to the auction market
- GUI-driven player flow
- English and Vietnamese GUI and message files
- Admin reload path

## Requirements

- Paper / Folia 1.21+
- Java 21

## Installation

1. Put the plugin jar in `plugins`.
2. Start the server once.
3. Review `config.yml`, `sounds.yml`, GUI files, and message files.
4. Restart the server.

## Commands

- `/ah` - Open the auction house.
- `/ah sell <price>` - List an item for sale.
- `/ah <search>` - Search auction entries.
- `/auction`, `/auctions`, `/auctionhouse` - Aliases.
- `/ahreload` - Reload plugin files.

## Files

- `config.yml` - Main plugin settings.
- `sounds.yml` - Sound configuration.
- `guis/en` and `guis/vi` - GUI layouts.
- `message_en.yml` and `message_vi.yml` - Message files.

## Notes

- This plugin is marked as Folia supported.
- Test listing flow, search flow, and GUI labels after editing localized files.