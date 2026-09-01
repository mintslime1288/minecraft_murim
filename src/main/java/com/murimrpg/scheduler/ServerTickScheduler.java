package com.murimrpg.scheduler;

import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/** Main-thread delayed work without wall-clock threads or per-system tick loops. */
public final class ServerTickScheduler {
    private final Queue<Task> tasks = new PriorityQueue<>();
    private final AtomicLong sequence = new AtomicLong();

    public void register() { ServerTickEvents.END_SERVER_TICK.register(this::tick); }

    public void schedule(MinecraftServer server, long delayTicks, Runnable action) {
        if (delayTicks < 1) throw new IllegalArgumentException("delayTicks must be positive");
        tasks.add(new Task(server.getTickCount() + delayTicks, sequence.incrementAndGet(), action));
    }

    private void tick(MinecraftServer server) {
        while (!tasks.isEmpty() && tasks.peek().dueTick <= server.getTickCount()) {
            tasks.remove().action.run();
        }
    }

    private record Task(long dueTick, long sequence, Runnable action) implements Comparable<Task> {
        @Override public int compareTo(Task other) {
            int byTick = Long.compare(dueTick, other.dueTick);
            return byTick != 0 ? byTick : Long.compare(sequence, other.sequence);
        }
    }
}
