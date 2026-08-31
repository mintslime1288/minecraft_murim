package com.murimrpg.ui;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.PlayerJourney;
import com.murimrpg.domain.QuestDefinition;
import com.murimrpg.service.PlayerContentService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/** Shows cached active-quest progress and restores the player's previous scoreboard when it ends. */
public final class QuestSidebar {
    private static final String OBJECTIVE_NAME = "murim_quest";
    private final PlayerContentService content;
    private final ContentCatalog catalog;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public QuestSidebar(PlayerContentService content, ContentCatalog catalog) {
        this.content = content;
        this.catalog = catalog;
    }

    public void tick(Player player) {
        Optional<View> view = content.cached(player.getUniqueId()).flatMap(this::view);
        Session session = sessions.get(player.getUniqueId());
        if (view.isEmpty()) {
            if (session != null) restore(player, session);
            return;
        }
        if (session != null && player.getScoreboard() != session.scoreboard()) {
            session.objective().unregister();
            sessions.remove(player.getUniqueId());
            session = null;
        }
        if (session == null) {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", view.orElseThrow().title());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            session = new Session(player.getScoreboard(), scoreboard, objective, "");
            sessions.put(player.getUniqueId(), session);
            player.setScoreboard(scoreboard);
        }
        View value = view.orElseThrow();
        String fingerprint = value.title() + String.join("\n", value.lines());
        if (fingerprint.equals(session.fingerprint())) return;
        for (String entry : session.scoreboard().getEntries()) session.scoreboard().resetScores(entry);
        session.objective().setDisplayName(value.title());
        int score = value.lines().size();
        for (int index = 0; index < value.lines().size(); index++) {
            session.objective().getScore(value.lines().get(index) + "§" + Integer.toHexString(index)).setScore(score--);
        }
        sessions.put(player.getUniqueId(), new Session(session.previous(), session.scoreboard(), session.objective(), fingerprint));
    }

    public void clear(Player player) {
        Optional.ofNullable(sessions.get(player.getUniqueId())).ifPresent(value -> restore(player, value));
    }

    public void clearAll() {
        List.copyOf(Bukkit.getOnlinePlayers()).forEach(this::clear);
    }

    private Optional<View> view(PlayerJourney state) {
        return state.journey().activeActivityId().flatMap(catalog.quests()::find).map(quest -> new View(
                "§5진행 중 퀘스트", lines(quest, state)));
    }

    private List<String> lines(QuestDefinition quest, PlayerJourney state) {
        int hits = Math.min(state.journey().dummyHits(), quest.requiredHits());
        int meditation = Math.min(state.journey().meditationSeconds(), quest.requiredMeditationSeconds());
        return List.of("§f" + quest.displayName(), "§e타격: §f" + hits + "§7/§f" + quest.requiredHits(),
                "§e운기: §f" + meditation + "§7/§f" + quest.requiredMeditationSeconds() + "초");
    }

    private void restore(Player player, Session session) {
        sessions.remove(player.getUniqueId(), session);
        if (player.getScoreboard() == session.scoreboard()) player.setScoreboard(session.previous());
        session.objective().unregister();
    }

    private record View(String title, List<String> lines) { }
    private record Session(Scoreboard previous, Scoreboard scoreboard, Objective objective, String fingerprint) { }
}
