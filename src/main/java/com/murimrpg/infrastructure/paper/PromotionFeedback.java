package com.murimrpg.infrastructure.paper;

import com.murimrpg.domain.PromotionResult;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

public final class PromotionFeedback {
    private final PromotionFeedbackSettings settings;

    public PromotionFeedback(PromotionFeedbackSettings settings) {
        this.settings = settings;
    }

    public void showSuccess(Player player, PromotionResult result, List<Component> chatMessages) {
        if (result.status() != PromotionResult.Status.SUCCESS) {
            throw new IllegalArgumentException("Promotion feedback requires a successful promotion result");
        }

        String previous = result.currentStage().orElseThrow().displayName();
        String current = result.nextStage().orElseThrow().displayName();
        if (settings.titleEnabled()) {
            player.showTitle(Title.title(
                    Component.text("경지 돌파", NamedTextColor.GOLD),
                    Component.text(previous + " → " + current, NamedTextColor.AQUA),
                    Title.Times.times(
                            Duration.ofMillis(250),
                            Duration.ofSeconds(2),
                            Duration.ofMillis(500)
                    )
            ));
        }
        if (settings.soundEnabled()) {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS,
                    0.7f,
                    1.15f
            );
        }
        if (settings.particlesEnabled() && settings.particleCount() > 0) {
            player.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    player.getLocation().add(0, 1, 0),
                    settings.particleCount(),
                    0.55,
                    0.7,
                    0.55,
                    0.0
            );
        }
        if (settings.chatEnabled()) {
            chatMessages.forEach(player::sendMessage);
        }
    }
}
