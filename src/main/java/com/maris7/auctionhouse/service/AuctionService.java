package com.maris7.auctionhouse.service;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.db.DatabaseManager;
import com.maris7.auctionhouse.model.AuctionEntry;
import com.maris7.auctionhouse.model.ClaimEntry;
import com.maris7.auctionhouse.model.TransactionEntry;
import com.maris7.auctionhouse.util.FoliaScheduler;
import com.maris7.auctionhouse.util.ItemSerializer;
import com.maris7.auctionhouse.util.ItemUtil;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AuctionService {

    private final MarisAuctionPlugin plugin;
    private final DatabaseManager database;

    public AuctionService(MarisAuctionPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    public CompletableFuture<Void> list(Player seller, ItemStack item, double price) {
        UUID sellerId = seller.getUniqueId();
        String sellerName = seller.getName();
        ItemStack storedItem = item.clone();
        return database.executeAsync(connection -> {
            sweepExpired(connection);
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO auctions(seller_uuid,seller_name,item_base64,price,expires_at,search_key,category,sold,buyer_uuid,buyer_name) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                ps.setString(1, sellerId.toString());
                ps.setString(2, sellerName);
                ps.setString(3, ItemSerializer.encode(storedItem));
                ps.setDouble(4, price);
                ps.setLong(5, System.currentTimeMillis() + plugin.getConfigRegistry().get("config.yml").getLong("listing-duration-ms", 172800000L));
                ps.setString(6, buildSearchKey(storedItem, sellerName));
                ps.setString(7, ItemUtil.categoryOf(storedItem));
                ps.setInt(8, 0);
                ps.setString(9, null);
                ps.setString(10, null);
                ps.executeUpdate();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            return null;
        });
    }

    public CompletableFuture<Integer> countActive(UUID seller) {
        return database.executeAsync(connection -> {
            sweepExpired(connection);
            try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM auctions WHERE seller_uuid=? AND sold=0 AND expires_at>?")) {
                ps.setString(1, seller.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    public CompletableFuture<List<AuctionEntry>> browse(BrowseRequest request) {
        return database.executeAsync(connection -> {
            sweepExpired(connection);
            List<AuctionEntry> list = new ArrayList<>();
            StringBuilder sql = new StringBuilder("SELECT * FROM auctions WHERE sold=0 AND expires_at>?");
            if (!"ALL".equalsIgnoreCase(request.category())) {
                sql.append(" AND category=?");
            }
            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                int index = 1;
                ps.setLong(index++, System.currentTimeMillis());
                if (!"ALL".equalsIgnoreCase(request.category())) {
                    ps.setString(index, request.category().toUpperCase(Locale.ROOT));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapAuction(rs));
                    }
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            if (request.query() != null && !request.query().isBlank()) {
                String normalizedQuery = ItemUtil.normalizeSearchText(request.query());
                list = list.stream()
                        .filter(entry -> matchesQuery(entry, normalizedQuery))
                        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            }
            list.sort(sortComparator(request.sortMode()));
            return list;
        });
    }

    public CompletableFuture<List<AuctionEntry>> getOwn(UUID seller) {
        return database.executeAsync(connection -> {
            sweepExpired(connection);
            List<AuctionEntry> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM auctions WHERE seller_uuid=? AND sold=0 AND expires_at>? ORDER BY id DESC")) {
                ps.setString(1, seller.toString());
                ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapAuction(rs));
                    }
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            return list;
        });
    }

    public CompletableFuture<List<TransactionEntry>> getTransactions(UUID owner) {
        return database.executeAsync(connection -> {
            List<TransactionEntry> list = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM transactions WHERE owner_uuid=? ORDER BY id DESC")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new TransactionEntry(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("owner_uuid")),
                                rs.getString("counterparty_uuid") == null ? null : UUID.fromString(rs.getString("counterparty_uuid")),
                                rs.getString("counterparty_name"),
                                ItemSerializer.decode(rs.getString("item_base64")),
                                rs.getDouble("price"),
                                rs.getInt("purchase") == 1,
                                rs.getLong("created_at")
                        ));
                    }
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            return list;
        });
    }

    public CompletableFuture<BuyResult> buy(Player buyer, long auctionId) {
        UUID buyerId = buyer.getUniqueId();
        String buyerName = buyer.getName();
        return database.executeAsync(connection -> {
            OfflinePlayer seller = null;
            AuctionEntry entry = null;
            double entryPrice = 0D;
            boolean buyerCharged = false;
            boolean sellerPaid = false;
            try {
                sweepExpired(connection);
                connection.setAutoCommit(false);

                entry = findAuction(connection, auctionId);
                if (entry == null) {
                    connection.rollback();
                    return BuyResult.alreadySold();
                }
                AuctionEntry currentEntry = entry;
                entryPrice = currentEntry.price();
                double currentPrice = entryPrice;
                if (currentEntry.seller().equals(buyerId)) {
                    connection.rollback();
                    return BuyResult.selfBuy();
                }
                if (!FoliaScheduler.callEntity(plugin, buyer, () -> plugin.getEconomy().has(buyer, currentPrice)).join()) {
                    connection.rollback();
                    return BuyResult.insufficientFunds();
                }

                try (PreparedStatement update = connection.prepareStatement("UPDATE auctions SET sold=1,buyer_uuid=?,buyer_name=? WHERE id=? AND sold=0")) {
                    update.setString(1, buyerId.toString());
                    update.setString(2, buyerName);
                    update.setLong(3, auctionId);
                    if (update.executeUpdate() == 0) {
                        connection.rollback();
                        return BuyResult.alreadySold();
                    }
                }

                EconomyResponse withdraw = FoliaScheduler.callEntity(plugin, buyer, () -> plugin.getEconomy().withdrawPlayer(buyer, currentPrice)).join();
                if (!withdraw.transactionSuccess()) {
                    connection.rollback();
                    return BuyResult.insufficientFunds();
                }
                buyerCharged = true;

                UUID sellerId = currentEntry.seller();
                seller = FoliaScheduler.callGlobal(plugin, () -> org.bukkit.Bukkit.getOfflinePlayer(sellerId)).join();
                OfflinePlayer sellerAccount = seller;
                EconomyResponse deposit = FoliaScheduler.callGlobal(plugin, () -> plugin.getEconomy().depositPlayer(sellerAccount, currentPrice)).join();
                if (!deposit.transactionSuccess()) {
                    refundBuyer(buyer, entryPrice);
                    buyerCharged = false;
                    connection.rollback();
                    return BuyResult.failed();
                }
                sellerPaid = true;

                insertTransaction(connection, buyer.getUniqueId(), entry.seller(), entry.sellerName(), entry.item(), entryPrice, true);
                insertTransaction(connection, entry.seller(), buyer.getUniqueId(), buyer.getName(), entry.item(), entryPrice, false);
                connection.commit();
                return BuyResult.success(entry);
            } catch (Exception ex) {
                if (sellerPaid && seller != null) {
                    withdrawSeller(seller, auctionId, entryPrice);
                }
                if (buyerCharged) {
                    refundBuyer(buyer, auctionId, entryPrice);
                }
                try {
                    connection.rollback();
                } catch (Exception rollbackEx) {
                    ex.addSuppressed(rollbackEx);
                }
                throw new IllegalStateException(ex);
            }
        });
    }

    private void refundBuyer(Player buyer, double price) {
        EconomyResponse response = FoliaScheduler.callEntity(plugin, buyer, () -> plugin.getEconomy().depositPlayer(buyer, price)).join();
        if (!response.transactionSuccess()) {
            plugin.getLogger().severe("Failed to refund buyer " + buyer.getUniqueId() + " for " + price + " after a failed auction purchase.");
        }
    }

    private void refundBuyer(Player buyer, long auctionId, double price) {
        EconomyResponse response = FoliaScheduler.callEntity(plugin, buyer, () -> plugin.getEconomy().depositPlayer(buyer, price)).join();
        if (!response.transactionSuccess()) {
            plugin.getLogger().severe("Failed to refund buyer " + buyer.getUniqueId() + " while rolling back auction #" + auctionId + '.');
        }
    }

    private void withdrawSeller(OfflinePlayer seller, long auctionId, double price) {
        EconomyResponse response = FoliaScheduler.callGlobal(plugin, () -> plugin.getEconomy().withdrawPlayer(seller, price)).join();
        if (!response.transactionSuccess()) {
            plugin.getLogger().severe("Failed to withdraw funds back from seller " + seller.getUniqueId() + " while rolling back auction #" + auctionId + '.');
        }
    }

    public CompletableFuture<CancelResult> cancelListing(UUID owner, long auctionId) {
        return database.executeAsync(connection -> {
            try {
                sweepExpired(connection);
                connection.setAutoCommit(false);
                AuctionEntry entry = findAuction(connection, auctionId);
                if (entry == null || !entry.seller().equals(owner)) {
                    connection.rollback();
                    return CancelResult.notFound();
                }
                queueClaim(connection, owner, entry.item(), "CANCELLED");
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM auctions WHERE id=?")) {
                    delete.setLong(1, auctionId);
                    delete.executeUpdate();
                }
                connection.commit();
                return CancelResult.success();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    public CompletableFuture<List<ClaimEntry>> getClaims(UUID owner) {
        return database.executeAsync(connection -> {
            sweepExpired(connection);
            List<ClaimEntry> claims = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM claims WHERE owner_uuid=? ORDER BY id ASC")) {
                ps.setString(1, owner.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        claims.add(new ClaimEntry(
                                rs.getLong("id"),
                                UUID.fromString(rs.getString("owner_uuid")),
                                ItemSerializer.decode(rs.getString("item_base64")),
                                rs.getString("reason"),
                                rs.getLong("created_at")
                        ));
                    }
                }
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            return claims;
        });
    }

    public CompletableFuture<Integer> deleteClaims(UUID owner, List<Long> claimIds) {
        if (claimIds.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return database.executeAsync(connection -> {
            String placeholders = String.join(",", claimIds.stream().map(id -> "?").toList());
            String sql = "DELETE FROM claims WHERE owner_uuid=? AND id IN (" + placeholders + ")";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                int index = 1;
                ps.setString(index++, owner.toString());
                for (Long claimId : claimIds) {
                    ps.setLong(index++, claimId);
                }
                return ps.executeUpdate();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    public CompletableFuture<Integer> sweepExpiredAsync() {
        return database.executeAsync(this::sweepExpired);
    }

    private int sweepExpired(Connection connection) {
        int moved = 0;
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM auctions WHERE sold=0 AND expires_at<=?")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AuctionEntry entry = mapAuction(rs);
                    queueClaim(connection, entry.seller(), entry.item(), "EXPIRED");
                    moved++;
                }
            }
            if (moved > 0) {
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM auctions WHERE sold=0 AND expires_at<=?")) {
                    delete.setLong(1, System.currentTimeMillis());
                    delete.executeUpdate();
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return moved;
    }

    private AuctionEntry findAuction(Connection connection, long auctionId) throws Exception {
        try (PreparedStatement check = connection.prepareStatement("SELECT * FROM auctions WHERE id=? AND sold=0 AND expires_at>?")) {
            check.setLong(1, auctionId);
            check.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return mapAuction(rs);
            }
        }
    }

    private void queueClaim(Connection connection, UUID owner, ItemStack item, String reason) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO claims(owner_uuid,item_base64,reason,created_at) VALUES(?,?,?,?)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, ItemSerializer.encode(item));
            ps.setString(3, reason);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private void insertTransaction(Connection connection, UUID owner, UUID counterparty, String counterpartyName, ItemStack item, double price, boolean purchase) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO transactions(owner_uuid,counterparty_uuid,counterparty_name,item_base64,price,purchase,created_at) VALUES(?,?,?,?,?,?,?)")) {
            ps.setString(1, owner.toString());
            ps.setString(2, counterparty == null ? null : counterparty.toString());
            ps.setString(3, counterpartyName);
            ps.setString(4, ItemSerializer.encode(item));
            ps.setDouble(5, price);
            ps.setInt(6, purchase ? 1 : 0);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    private Comparator<AuctionEntry> sortComparator(String sortMode) {
        return switch (sortMode.toUpperCase(Locale.ROOT)) {
            case "HIGHEST_PRICE" -> Comparator.comparingDouble(AuctionEntry::price).reversed().thenComparing(AuctionEntry::id).reversed();
            case "LOWEST_PRICE" -> Comparator.comparingDouble(AuctionEntry::price).thenComparing(AuctionEntry::id).reversed();
            default -> Comparator.comparingLong(AuctionEntry::id).reversed();
        };
    }

    private String buildSearchKey(ItemStack item, String sellerName) {
        return ItemUtil.normalizeSearchText(ItemUtil.resolveName(item) + " " + item.getType().name() + " " + sellerName + " " + ItemUtil.categoryOf(item));
    }

    private boolean matchesQuery(AuctionEntry entry, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        String normalizedSearchKey = ItemUtil.normalizeSearchText(entry.searchKey());
        if (normalizedSearchKey.contains(normalizedQuery)) {
            return true;
        }
        String normalizedName = ItemUtil.normalizeSearchText(ItemUtil.resolveName(entry.item()));
        if (normalizedName.contains(normalizedQuery)) {
            return true;
        }
        String normalizedMaterial = ItemUtil.normalizeSearchText(entry.item().getType().name());
        if (normalizedMaterial.contains(normalizedQuery)) {
            return true;
        }
        return ItemUtil.normalizeSearchText(entry.sellerName()).contains(normalizedQuery);
    }

    private AuctionEntry mapAuction(ResultSet rs) throws Exception {
        return new AuctionEntry(
                rs.getLong("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                ItemSerializer.decode(rs.getString("item_base64")),
                rs.getDouble("price"),
                rs.getLong("expires_at"),
                rs.getString("search_key"),
                rs.getString("category"),
                rs.getInt("sold") == 1,
                rs.getString("buyer_uuid") == null ? null : UUID.fromString(rs.getString("buyer_uuid")),
                rs.getString("buyer_name")
        );
    }

    public record BrowseRequest(String query, String category, String sortMode) {}

    public record BuyResult(Status status, AuctionEntry entry) {
        public static BuyResult success(AuctionEntry entry) { return new BuyResult(Status.SUCCESS, entry); }
        public static BuyResult alreadySold() { return new BuyResult(Status.ALREADY_SOLD, null); }
        public static BuyResult insufficientFunds() { return new BuyResult(Status.INSUFFICIENT_FUNDS, null); }
        public static BuyResult selfBuy() { return new BuyResult(Status.SELF_BUY, null); }
        public static BuyResult failed() { return new BuyResult(Status.FAILED, null); }
    }

    public record CancelResult(Status status) {
        public static CancelResult success() { return new CancelResult(Status.SUCCESS); }
        public static CancelResult notFound() { return new CancelResult(Status.NOT_FOUND); }
    }

    public enum Status {
        SUCCESS,
        ALREADY_SOLD,
        INSUFFICIENT_FUNDS,
        SELF_BUY,
        FAILED,
        NOT_FOUND
    }
}
