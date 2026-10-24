package com.murimrpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import org.slf4j.LoggerFactory;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** Local-only A/B switch and bounded render diagnostics; never sends server commands. */
public final class NaturalWalkDiagnostics {
    public enum Mode { AUTO, WALK, RUN }
    private static boolean enabled = true;
    private static boolean debug;
    private static Mode mode = Mode.AUTO;
    private static Runnable reload = () -> { };
    private static long calls;
    private static long lastLog;
    private static long lastSample;
    private static String last = "아직 플레이어 모델 호출 없음: F5로 전환하세요";
    private NaturalWalkDiagnostics() { }
    public static boolean enabled() { return enabled; }
    public static Mode mode() { return mode; }
    private static LiteralArgumentBuilder<FabricClientCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public static void record(int entityId, String reason, float speed, float phase) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getId() != entityId) return;
        calls++;
        long now = System.nanoTime();
        // Avoid string formatting/allocation on every rendered frame.
        if (now - lastSample < 250_000_000L) return;
        lastSample = now;
        last = "상태=" + reason + " 속도=" + String.format(java.util.Locale.ROOT, "%.3f", speed)
                + " 주기=" + String.format(java.util.Locale.ROOT, "%.3f", phase);
        if (debug && now - lastLog >= 5_000_000_000L) {
            lastLog = now;
            LoggerFactory.getLogger("MurimRPG-Walk").info("Natural walk render: {} calls={}", last, calls);
        }
    }

    public static void register(Runnable reloadAction) {
        reload = reloadAction;
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                literal("murimwalk")
                        .executes(context -> report(context.getSource()))
                        .then(literal("status").executes(context -> report(context.getSource())))
                        .then(literal("on").executes(context -> { enabled = true; return report(context.getSource()); }))
                        .then(literal("off").executes(context -> { enabled = false; return report(context.getSource()); }))
                        .then(literal("reload").executes(context -> {
                            try {
                                reload.run();
                                var client = Minecraft.getInstance();
                                com.murimrpg.client.render.WuxiaPlayerRenderer.loadResources(client);
                                context.getSource().sendFeedback(Component.literal("[걷기] 설정과 GLB를 다시 불러왔습니다.")); return 1; }
                            catch (RuntimeException error) { context.getSource().sendError(Component.literal("[걷기] 다시 불러오기 실패: " + error.getMessage())); return 0; }
                        }))
                        .then(literal("mode")
                                .then(literal("auto").executes(context -> { mode = Mode.AUTO; return report(context.getSource()); }))
                                .then(literal("walk").executes(context -> { mode = Mode.WALK; return report(context.getSource()); }))
                                .then(literal("run").executes(context -> { mode = Mode.RUN; return report(context.getSource()); })))
                        .then(literal("debug").then(literal("on").executes(context -> {
                            debug = true; return report(context.getSource());
                        })).then(literal("off").executes(context -> {
                            debug = false; return report(context.getSource());
                        })))));
        LoggerFactory.getLogger("MurimRPG-Walk").info("Locomotion diagnostics ready: /murimwalk on|off|status|reload|mode auto|walk|run|debug on|off");
    }

    private static int report(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("[걷기] " + (enabled ? "켜짐" : "꺼짐")
                + " 모드=" + mode.name().toLowerCase(java.util.Locale.ROOT)
                + " 방식=네이티브_절차보행"
                + " 전체모델=" + com.murimrpg.client.render.AnimatedPlayerGlbRenderer.status()
                + " | 무협모델=" + com.murimrpg.client.render.WuxiaPlayerRenderer.status()
                + " | 관절리그=" + com.murimrpg.client.render.ArticulatedPlayerRenderer.status()
                + " | 네이티브관절=" + com.murimrpg.client.render.ModelPartArticulatedPlayerRenderer.status()
                + " 진단=" + debug + " 호출=" + calls + " " + last));
        return 1;
    }
}
