package com.maris7.auctionhouse.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class FoliaScheduler {

    private static final boolean FOLIA = detectFolia();

    private FoliaScheduler() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void runAsync(JavaPlugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }

        Object scheduler = invokeStatic(Bukkit.class, "getAsyncScheduler");
        invoke(findMethod(scheduler.getClass(), "runNow", Plugin.class, Consumer.class), scheduler, plugin, consumer(task));
    }

    public static TaskHandle runAsyncTimer(JavaPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (!FOLIA) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, initialDelayTicks, periodTicks));
        }

        Object scheduler = invokeStatic(Bukkit.class, "getAsyncScheduler");
        Object handle = invoke(
                findMethod(scheduler.getClass(), "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class),
                scheduler,
                plugin,
                consumer(task),
                ticksToMillis(initialDelayTicks),
                ticksToMillis(periodTicks),
                TimeUnit.MILLISECONDS
        );
        return new ReflectiveTaskHandle(handle);
    }

    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        Object scheduler = invokeStatic(Bukkit.class, "getGlobalRegionScheduler");
        invoke(findMethod(scheduler.getClass(), "run", Plugin.class, Consumer.class), scheduler, plugin, consumer(task));
    }

    public static void runGlobalLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return;
        }

        Object scheduler = invokeStatic(Bukkit.class, "getGlobalRegionScheduler");
        invoke(findMethod(scheduler.getClass(), "runDelayed", Plugin.class, Consumer.class, long.class), scheduler, plugin, consumer(task), sanitizeDelay(delayTicks));
    }

    public static TaskHandle runGlobalTimer(JavaPlugin plugin, Runnable task, long initialDelayTicks, long periodTicks) {
        if (!FOLIA) {
            return new BukkitTaskHandle(Bukkit.getScheduler().runTaskTimer(plugin, task, initialDelayTicks, periodTicks));
        }

        Object scheduler = invokeStatic(Bukkit.class, "getGlobalRegionScheduler");
        Object handle = invoke(
                findMethod(scheduler.getClass(), "runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class),
                scheduler,
                plugin,
                consumer(task),
                sanitizeDelay(initialDelayTicks),
                sanitizePeriod(periodTicks)
        );
        return new ReflectiveTaskHandle(handle);
    }

    public static boolean runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return true;
        }
        return scheduleEntity(plugin, entity, task, 1L);
    }

    public static boolean runEntityLater(JavaPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return true;
        }
        return scheduleEntity(plugin, entity, task, delayTicks);
    }

    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        Object scheduler = invokeStatic(Bukkit.class, "getRegionScheduler");
        invoke(findMethod(scheduler.getClass(), "execute", Plugin.class, Location.class, Runnable.class), scheduler, plugin, location, task);
    }

    public static <T> CompletableFuture<T> callEntity(JavaPlugin plugin, Entity entity, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!runEntity(plugin, entity, () -> completeFuture(future, supplier))) {
            future.completeExceptionally(new IllegalStateException("Unable to schedule entity task"));
        }
        return future;
    }

    public static <T> CompletableFuture<T> callGlobal(JavaPlugin plugin, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runGlobal(plugin, () -> completeFuture(future, supplier));
        return future;
    }

    private static <T> void completeFuture(CompletableFuture<T> future, Supplier<T> supplier) {
        try {
            future.complete(supplier.get());
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }

    private static boolean scheduleEntity(JavaPlugin plugin, Entity entity, Runnable task, long delayTicks) {
        Object scheduler = invoke(findMethod(entity.getClass(), "getScheduler"), entity);
        Object handle = invoke(
                findMethod(scheduler.getClass(), "runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class),
                scheduler,
                plugin,
                consumer(task),
                null,
                sanitizeDelay(delayTicks)
        );
        return handle != null;
    }

    private static Consumer<Object> consumer(Runnable task) {
        return ignored -> task.run();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method;
            try {
                method = type.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                method = type.getDeclaredMethod(name, parameterTypes);
            }
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Missing Folia scheduler method: " + type.getName() + '#' + name, ex);
        }
    }

    private static Object invokeStatic(Class<?> type, String name) {
        return invoke(findMethod(type, name), null);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to invoke " + method.getDeclaringClass().getName() + '#' + method.getName(), ex);
        }
    }

    private static boolean detectFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private static long ticksToMillis(long ticks) {
        return Math.max(0L, ticks) * 50L;
    }

    private static long sanitizeDelay(long ticks) {
        return Math.max(1L, ticks);
    }

    private static long sanitizePeriod(long ticks) {
        return Math.max(1L, ticks);
    }

    public interface TaskHandle {
        void cancel();
    }

    private record BukkitTaskHandle(BukkitTask task) implements TaskHandle {
        @Override
        public void cancel() {
            task.cancel();
        }
    }

    private record ReflectiveTaskHandle(Object handle) implements TaskHandle {
        @Override
        public void cancel() {
            invoke(findMethod(handle.getClass(), "cancel"), handle);
        }
    }
}
