package com.murimrpg.presentation;

import com.murimrpg.domain.PluginStatus;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.service.ProfileLoadState;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class StatusMessageFactory {
    public List<Component> status(PluginStatus status) {
        return List.of(
                Component.text("MurimRPG status", NamedTextColor.GOLD),
                line("Plugin name: ", status.pluginName(), NamedTextColor.AQUA),
                line("Plugin version: ", status.pluginVersion(), NamedTextColor.GREEN),
                line("Paper API: ", status.paperApiVersion(), NamedTextColor.LIGHT_PURPLE),
                line("Data store: ", status.dataStoreStatus(), NamedTextColor.YELLOW)
        );
    }

    public Component usage(String label) {
        return Component.text("사용법: /" + label + " <status|profile|admin>", NamedTextColor.RED);
    }

    public List<Component> profile(PlayerProfile profile, ProfileLoadState loadState) {
        String shortUuid = profile.uuid().toString().substring(0, 8);
        return List.of(
                Component.text("MurimRPG 프로필", NamedTextColor.GOLD),
                line("UUID: ", shortUuid + "…", NamedTextColor.GRAY),
                line("경지: ", "[" + profile.realmDisplayName() + "]", NamedTextColor.AQUA),
                line("수련 EXP: ", Long.toString(profile.cultivationExp()), NamedTextColor.GREEN),
                line("완료한 승급 조건: ", profile.completedRequirements().size() + "개", NamedTextColor.YELLOW),
                line("해금 콘텐츠: ", profile.unlockedContent().size() + "개", NamedTextColor.LIGHT_PURPLE),
                line("데이터 로드 상태: ", loadState.displayName(), NamedTextColor.WHITE)
        );
    }

    public Component info(String message) {
        return Component.text(message, NamedTextColor.YELLOW);
    }

    public Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    public Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }
}
