package com.murimrpg.listener;

import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.domain.CultivationStageDefinition;
import com.murimrpg.domain.HuashanSettings;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.infrastructure.paper.HuashanTrainingDummy;
import com.murimrpg.infrastructure.paper.HuashanExamEntities;
import com.murimrpg.service.HuashanService;
import com.murimrpg.service.MeditationService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class HuashanGameplayListener implements Listener {
    private static final int SCHEDULER_PERIOD_TICKS = 5;
    private final JavaPlugin plugin;
    private final PlayerContentService contentService;
    private final PlayerProfileService profileService;
    private final CultivationProgression progression;
    private final HuashanService huashanService;
    private final MeditationService meditationService;
    private final HuashanTrainingDummy dummy;
    private final HuashanExamEntities examEntities;
    private final HuashanSettings settings;
    private final Executor mainThreadExecutor;
    private final Logger logger;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final BukkitTask task;

    public HuashanGameplayListener(JavaPlugin plugin, PlayerContentService contentService,
            PlayerProfileService profileService, CultivationProgression progression,
            HuashanService huashanService, HuashanTrainingDummy dummy, HuashanExamEntities examEntities,
            MeditationService meditationService,
            HuashanSettings settings,
            Executor mainThreadExecutor, Logger logger) {
        this.plugin = plugin;
        this.contentService = contentService;
        this.profileService = profileService;
        this.progression = progression;
        this.huashanService = huashanService;
        this.meditationService = meditationService; this.examEntities = examEntities;
        this.dummy = dummy;
        this.settings = settings;
        this.mainThreadExecutor = mainThreadExecutor;
        this.logger = logger;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, SCHEDULER_PERIOD_TICKS,
                SCHEDULER_PERIOD_TICKS);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            cancel(event.getPlayer().getUniqueId(), true);
            return;
        }
        Player player = event.getPlayer();
        if (!player.isOnGround()) return;
        contentService.cached(player.getUniqueId()).filter(meditationService::canStart).ifPresent(ignored ->
                sessions.putIfAbsent(player.getUniqueId(), new Session(player.getLocation().clone())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) cancel(event.getPlayer().getUniqueId(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            cancel(attacker.getUniqueId(), true);
            if (dummy.isTrainingDummy(event.getEntity())) {
                event.setCancelled(true);
                if (attacker.getInventory().getItemInMainHand().getType() == Material.WOODEN_SWORD) {
                    huashanService.recordDummyHit(attacker.getUniqueId(),
                            settings.trainingDummyId())
                            .whenCompleteAsync((result, error) -> report(attacker,
                                    result != null && result.activityCompleted() ? result.message() : null, error),
                                    mainThreadExecutor);
                }
            } else if (examEntities.contentId(event.getEntity()).filter(settings.examinerId()::equals).isPresent()) {
                if (!examEntities.owner(event.getEntity()).filter(attacker.getUniqueId()::equals).isPresent()) {
                    event.setCancelled(true);
                    attacker.sendMessage(Component.text("이 시험관은 다른 수련자의 승급 시험 대상입니다."));
                    return;
                }
                double remaining = Math.max(0, event.getEntity() instanceof org.bukkit.entity.Damageable damageable
                        ? damageable.getHealth() - event.getFinalDamage() : 0);
                double percent = remaining / settings.examinerMaxHealth() * 100.0;
                if (percent <= huashanService.examinerPassThresholdPercent()) event.setCancelled(true);
                huashanService.recordExaminerHealth(attacker.getUniqueId(), settings.examinerId(), percent)
                        .whenCompleteAsync((result, error) -> {
                            report(attacker, result != null && result.activityCompleted() ? result.message() : null, error);
                            if (result != null && result.activityCompleted()) event.getEntity().remove();
                        }, mainThreadExecutor);
            }
        }
        if (event.getEntity() instanceof Player victim) cancel(victim.getUniqueId(), true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!examEntities.contentId(event.getEntity()).filter("BANDIT_LEADER"::equals).isPresent()) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !examEntities.owner(event.getEntity()).filter(killer.getUniqueId()::equals).isPresent()) return;
        huashanService.recordMonsterKill(killer.getUniqueId(), "BANDIT_LEADER")
                .whenCompleteAsync((result, error) -> report(killer,
                        result != null && result.activityCompleted() ? result.message() : null, error), mainThreadExecutor);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (examEntities.contentId(event.getEntity()).filter(settings.examinerId()::equals).isPresent()
                && !(event instanceof EntityDamageByEntityEvent byEntity && byEntity.getDamager() instanceof Player)) {
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player player) cancel(player.getUniqueId(), true);
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) { cancel(event.getPlayer().getUniqueId(), true); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer().getUniqueId(), true); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent event) { cancel(event.getPlayer().getUniqueId(), true); }

    public void shutdown() {
        for (UUID uuid : java.util.List.copyOf(sessions.keySet())) cancel(uuid, true);
        task.cancel();
    }

    private void tick() {
        sessions.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            Session session = entry.getValue();
            if (player == null || !player.isOnline() || !player.isSneaking() || !player.isOnGround()
                    || changedPosition(session.origin(), player.getLocation())) {
                meditationService.cancel(entry.getKey());
                return true;
            }
            session.ticks += SCHEDULER_PERIOD_TICKS;
            int preparationTicks = settings.meditationPreparationSeconds() * 20;
            if (session.ticks < preparationTicks) {
                player.sendActionBar(Component.text("운기조식 준비 "
                        + (preparationTicks - session.ticks + 19) / 20 + "초"));
                return false;
            }
            PlayerJourney journey = contentService.cached(entry.getKey()).orElse(null);
            session.particleTicks += SCHEDULER_PERIOD_TICKS;
            if (journey != null && journey.journey().activeSkillId().filter("PURPLE_MIST_DIVINE_ART"::equals).isPresent()) {
                if (session.particleTicks >= settings.particleIntervalTicks()) {
                    session.particleTicks %= settings.particleIntervalTicks();
                    player.getWorld().spawnParticle(Particle.valueOf(settings.particleType()),
                            player.getLocation().add(0, 1, 0), settings.particleCount(), .6, .8, .6, .01);
                }
            } else {
                session.particleTicks = 0;
            }
            if ((session.ticks - preparationTicks) % 20 != 0) return false;
            PlayerProfile profile = profileService.cachedProfile(entry.getKey()).orElse(null);
            if (profile == null) return false;
            CultivationStageDefinition stage = progression.stage(profile);
            meditationService.progressSecond(entry.getKey(), stage.maxQi(), stage.qiRegeneration())
                    .whenCompleteAsync((result, error) -> {
                        if (error != null) {
                            report(player, null, error);
                            cancel(player.getUniqueId(), false);
                            return;
                        }
                        if (player.isOnline()) {
                            long qi = result.state().journey().currentQi();
                            player.sendActionBar(Component.text("내공 " + qi + " / " + stage.maxQi()));
                            if (result.activityCompleted()) player.sendMessage(Component.text(result.message()));
                        }
                    }, mainThreadExecutor);
            return false;
        });
    }

    private void cancel(UUID uuid, boolean resetContinuous) {
        if (sessions.remove(uuid) != null && resetContinuous) {
            meditationService.cancel(uuid).whenComplete((result, error) -> {
                if (error != null) logger.log(Level.WARNING, "Could not cancel meditation for " + uuid, root(error));
            });
        }
    }

    private boolean changedPosition(Location origin, Location current) {
        return origin.getWorld() != current.getWorld() || origin.getX() != current.getX()
                || origin.getY() != current.getY() || origin.getZ() != current.getZ();
    }

    private void report(Player player, String message, Throwable error) {
        if (error != null) {
            logger.log(Level.WARNING, "Huashan gameplay action failed for " + player.getUniqueId(), root(error));
            player.sendMessage(Component.text("처리 실패: " + root(error).getMessage()));
        } else if (message != null) player.sendMessage(Component.text(message));
    }

    private Throwable root(Throwable value) {
        while (value.getCause() != null) value = value.getCause();
        return value;
    }

    private static final class Session {
        private final Location origin;
        private int ticks;
        private int particleTicks;
        private Session(Location origin) { this.origin = origin; }
        private Location origin() { return origin; }
    }
}
