package com.murimrpg.infrastructure.paper;

import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class BukkitMainThreadExecutor implements Executor {
    private final Plugin plugin;

    public BukkitMainThreadExecutor(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Runnable command) {
        if (Bukkit.isPrimaryThread()) {
            command.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, command);
    }
}
