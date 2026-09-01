package com.murimrpg.ui;

import com.murimrpg.service.PlayerContentService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Per-player sidebar packets avoid replacing the server's shared scoreboard. */
final class QuestSidebar {
    private final PlayerContentService content;
    private final UiViewService views;
    private final Map<UUID, Session> sessions = new HashMap<>();
    QuestSidebar(PlayerContentService content, UiViewService views) { this.content = content; this.views = views; }

    void tick(ServerPlayer player) {
        Optional<QuestSidebarView> next = content.cached(player.getUUID()).flatMap(views::activeQuest);
        Session current = sessions.get(player.getUUID());
        if (next.isEmpty()) { if (current != null) clear(player); return; }
        QuestSidebarView view = next.orElseThrow();
        String fingerprint = view.title() + '\n' + String.join("\n", view.lines());
        if (current == null || !current.title.equals(view.title())) {
            if (current != null) clear(player);
            current = create(player, view.title());
        }
        if (fingerprint.equals(current.fingerprint)) return;
        for (String owner : current.owners) player.connection.send(new ClientboundResetScorePacket(owner, current.objective.getName()));
        current.owners = render(player, current.objective, view.lines());
        current.fingerprint = fingerprint;
    }

    void clear(ServerPlayer player) {
        Session session = sessions.remove(player.getUUID());
        if (session == null) return;
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, null));
        player.connection.send(new ClientboundSetObjectivePacket(session.objective, ClientboundSetObjectivePacket.METHOD_REMOVE));
    }

    private Session create(ServerPlayer player, String title) {
        Scoreboard board = new Scoreboard();
        String name = "murim_" + player.getUUID().toString().substring(0, 8);
        Objective objective = board.addObjective(name, ObjectiveCriteria.DUMMY, Component.literal(title),
                ObjectiveCriteria.RenderType.INTEGER, false, BlankFormat.INSTANCE);
        player.connection.send(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        player.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR, objective));
        Session session = new Session(title, objective);
        sessions.put(player.getUUID(), session);
        return session;
    }

    private List<String> render(ServerPlayer player, Objective objective, List<String> lines) {
        java.util.ArrayList<String> owners = new java.util.ArrayList<>();
        int score = lines.size();
        for (int index = 0; index < lines.size(); index++) {
            String owner = "#murim_" + index;
            owners.add(owner);
            player.connection.send(new ClientboundSetScorePacket(owner, objective.getName(), score--,
                    Optional.of(Component.literal(lines.get(index))), Optional.of(BlankFormat.INSTANCE)));
        }
        return owners;
    }

    private static final class Session {
        private final String title; private final Objective objective;
        private String fingerprint = ""; private List<String> owners = List.of();
        private Session(String title, Objective objective) { this.title = title; this.objective = objective; }
    }
}
