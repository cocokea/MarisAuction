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

## Quick Setup

1. Configure storage and basic auction limits in `config.yml`.
2. Review the GUI files under `guis/en` and `guis/vi`.
3. Adjust `sounds.yml` if you do not want the default sound set.
4. Test listing an item with a staff account.
5. Test search flow and buy flow with a normal player account.

## Player Commands

- `/ah` - Open the auction house.
- `/ah sell <price>` - List an item for sale.
- `/ah <search>` - Search auction entries.
- `/auction`, `/auctions`, `/auctionhouse` - Aliases.

## Admin Command

- `/ahreload` - Reload plugin files.

## Command Examples

```text
/ah
/ah sell 50000
/ah diamond
/ahreload
```

## Files

- `config.yml` - Main plugin settings.
- `sounds.yml` - Sound configuration.
- `guis/en` and `guis/vi` - GUI layouts.
- `message_en.yml` and `message_vi.yml` - Message files.

## MarisSettings Integration

If `MarisSettings` is installed, MarisAuction can use:

- `AUCTION_TOGGLE`
- `AUCTION_FAST_BUY`
- `AUCTION_FAST_SELL`

## Common Mistakes

- Forgetting to validate GUI text after editing both language folders.
- Leaving fast-buy enabled in settings when you expect every purchase to confirm manually.
- Testing only search flow and not testing list, buy, cancel, and expired item flow together.

## Notes

- This plugin is marked as Folia supported.
- Test listing flow, search flow, and GUI labels after editing localized files.