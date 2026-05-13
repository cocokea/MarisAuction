package com.maris7.auctionhouse.db;

import com.maris7.auctionhouse.MarisAuctionPlugin;
import com.maris7.auctionhouse.util.FoliaScheduler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class DatabaseManager {

    private final MarisAuctionPlugin plugin;
    private HikariDataSource dataSource;
    private boolean mysqlMode;
    private final AtomicBoolean mysqlTimedOut = new AtomicBoolean(false);
    private FoliaScheduler.TaskHandle healthMonitorTask;

    public DatabaseManager(MarisAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getDataFolder().mkdirs();
        YamlConfiguration config = plugin.getConfigRegistry().get("config.yml");
        HikariConfig hikari = new HikariConfig();
        String type = config.getString("database.type", "sqlite");
        this.mysqlMode = "mysql".equalsIgnoreCase(type);
        if (mysqlMode) {
            hikari.setJdbcUrl("jdbc:mysql://" + config.getString("database.mysql.host", "127.0.0.1") + ':'
                    + config.getInt("database.mysql.port", 3306) + '/' + config.getString("database.mysql.database", "marisauction")
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikari.setUsername(config.getString("database.mysql.username", "root"));
            hikari.setPassword(config.getString("database.mysql.password", ""));
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
            hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            File dbFile = new File(plugin.getDataFolder(), "auction.db");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        }
        hikari.setMaximumPoolSize(config.getInt("database.pool.maximum-pool-size", 10));
        hikari.setMinimumIdle(config.getInt("database.pool.minimum-idle", 1));
        hikari.setPoolName("MarisAuctionPool");
        this.dataSource = new HikariDataSource(hikari);
        bootstrap();
        startHealthMonitor();
    }

    private void bootstrap() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String idColumn = mysqlMode ? "BIGINT PRIMARY KEY AUTO_INCREMENT" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS auctions (id " + idColumn + ", seller_uuid VARCHAR(36) NOT NULL, seller_name VARCHAR(32) NOT NULL, item_base64 TEXT NOT NULL, price DOUBLE NOT NULL, expires_at BIGINT NOT NULL, search_key TEXT NOT NULL, category VARCHAR(32) NOT NULL DEFAULT 'ALL', sold INTEGER NOT NULL DEFAULT 0, buyer_uuid VARCHAR(36), buyer_name VARCHAR(32))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS transactions (id " + idColumn + ", owner_uuid VARCHAR(36) NOT NULL, counterparty_uuid VARCHAR(36), counterparty_name VARCHAR(32), item_base64 TEXT NOT NULL, price DOUBLE NOT NULL, purchase INTEGER NOT NULL, created_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS claims (id " + idColumn + ", owner_uuid VARCHAR(36) NOT NULL, item_base64 TEXT NOT NULL, reason VARCHAR(32) NOT NULL, created_at BIGINT NOT NULL)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auctions_active ON auctions (sold, expires_at, category)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_auctions_seller ON auctions (seller_uuid, sold)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_transactions_owner ON transactions (owner_uuid, id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claims_owner ON claims (owner_uuid, id)");
            ensureColumn(statement, connection, "auctions", "category", "ALTER TABLE auctions ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'ALL'");
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to bootstrap database", ex);
        }
    }

    private void ensureColumn(Statement statement, Connection connection, String table, String column, String alterSql) throws Exception {
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, column)) {
            if (!rs.next()) {
                statement.executeUpdate(alterSql);
            }
        }
    }

    public boolean isMysqlMode() {
        return mysqlMode;
    }

    public boolean isAuctionAvailable() {
        return !mysqlMode || !mysqlTimedOut.get();
    }

    private void startHealthMonitor() {
        if (!mysqlMode) {
            return;
        }
        healthMonitorTask = FoliaScheduler.runAsyncTimer(plugin, () -> {
            if (mysqlTimedOut.get() || dataSource == null || dataSource.isClosed()) {
                if (healthMonitorTask != null) {
                    healthMonitorTask.cancel();
                    healthMonitorTask = null;
                }
                return;
            }
            try (Connection connection = dataSource.getConnection(); PreparedStatement ps = connection.prepareStatement("SELECT 1")) {
                ps.execute();
            } catch (Exception ex) {
                handleMysqlTimeout(ex);
                if (healthMonitorTask != null) {
                    healthMonitorTask.cancel();
                    healthMonitorTask = null;
                }
            }
        }, 20L * 20L, 20L * 20L);
    }

    public <T> CompletableFuture<T> executeAsync(Function<Connection, T> action) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!isAuctionAvailable()) {
            future.completeExceptionally(new IllegalStateException("MySQL timed out"));
            return future;
        }
        FoliaScheduler.runAsync(plugin, () -> {
            if (!isAuctionAvailable()) {
                future.completeExceptionally(new IllegalStateException("MySQL timed out"));
                return;
            }
            try (Connection connection = dataSource.getConnection()) {
                future.complete(action.apply(connection));
            } catch (Exception ex) {
                if (mysqlMode && isConnectionProblem(ex)) {
                    handleMysqlTimeout(ex);
                }
                future.completeExceptionally(ex);
                plugin.getLogger().severe("Database error: " + ex.getMessage());
            }
        });
        return future;
    }

    private boolean isConnectionProblem(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sql && sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("communications link failure")
                        || lower.contains("connection is not available")
                        || lower.contains("connection refused")
                        || lower.contains("timed out")
                        || lower.contains("the last packet successfully received")
                        || lower.contains("no operations allowed after connection closed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void handleMysqlTimeout(Throwable throwable) {
        if (!mysqlMode || !mysqlTimedOut.compareAndSet(false, true)) {
            return;
        }
        plugin.handleMysqlTimeout();
    }




    public void shutdown() {
        if (healthMonitorTask != null) {
            healthMonitorTask.cancel();
            healthMonitorTask = null;
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
