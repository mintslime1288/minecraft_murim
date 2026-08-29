package com.murimrpg.presentation;

import com.murimrpg.domain.PluginStatus;
import com.murimrpg.domain.PlayerProfile;
import com.murimrpg.domain.PromotionResult;
import com.murimrpg.domain.RealmProgressView;
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
        return Component.text(
                "사용법: /" + label + " <status|profile|realm|cultivate|admin>",
                NamedTextColor.RED
        );
    }

    public List<Component> profile(
            PlayerProfile profile,
            ProfileLoadState loadState,
            String dataStoreStatus
    ) {
        String shortUuid = profile.uuid().toString().substring(0, 8);
        return List.of(
                Component.text("MurimRPG 프로필", NamedTextColor.GOLD),
                line("UUID: ", shortUuid + "…", NamedTextColor.GRAY),
                line("경지: ", "[" + profile.realmDisplayName() + "]", NamedTextColor.AQUA),
                line("수련 EXP: ", Long.toString(profile.cultivationExp()), NamedTextColor.GREEN),
                line(
                        "완료 승급 조건: ",
                        formatValues(profile.completedRequirements(), "없음"),
                        NamedTextColor.YELLOW
                ),
                line("해금 콘텐츠: ", profile.unlockedContent().size() + "개", NamedTextColor.LIGHT_PURPLE),
                line(
                        "데이터 저장 상태: ",
                        dataStoreStatus + " / " + loadState.displayName(),
                        NamedTextColor.WHITE
                )
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

    public List<Component> realm(RealmProgressView view) {
        List<Component> messages = new java.util.ArrayList<>();
        messages.add(Component.text("MurimRPG 경지", NamedTextColor.GOLD));
        messages.add(line("현재 경지: ", "[" + view.currentStage().displayName() + "]", NamedTextColor.AQUA));
        if (view.nextStage().isEmpty()) {
            messages.add(line(
                    "현재 최고 경지: ",
                    "[" + view.currentStage().displayName() + "]",
                    NamedTextColor.GOLD
            ));
            messages.add(Component.text("다음 경지는 설정되어 있지 않습니다.", NamedTextColor.YELLOW));
        } else {
            var next = view.nextStage().orElseThrow();
            messages.add(line(
                    "수련: ",
                    view.profile().cultivationExp() + " / " + next.requiredCultivationExp(),
                    NamedTextColor.GREEN
            ));
            messages.add(line("다음 경지: ", "[" + next.displayName() + "]", NamedTextColor.LIGHT_PURPLE));
            messages.add(line("부족한 수련: ", Long.toString(view.missingExp()), NamedTextColor.YELLOW));
            messages.add(line(
                    "필요한 추가 조건: ",
                    formatValues(view.missingRequirements(), "없음"),
                    view.missingRequirements().isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED
            ));
            messages.add(line(
                    "다음 경지 해금: ",
                    formatValues(next.unlocks(), "없음"),
                    NamedTextColor.LIGHT_PURPLE
            ));
        }
        return List.copyOf(messages);
    }

    public Component cultivationActionBar(RealmProgressView view) {
        long targetExp = view.nextStage()
                .map(stage -> stage.requiredCultivationExp())
                .orElse(view.currentStage().requiredCultivationExp());
        return Component.text("[" + view.currentStage().displayName() + "] ", NamedTextColor.AQUA)
                .append(Component.text(
                        "수련 " + view.profile().cultivationExp() + "/" + targetExp,
                        NamedTextColor.GREEN
                ));
    }

    public List<Component> promotion(PromotionResult result) {
        return switch (result.status()) {
            case SUCCESS -> List.of(
                    Component.text("승급 성공", NamedTextColor.GOLD),
                    line(
                            "현재 경지: ",
                            "[" + result.nextStage().orElseThrow().displayName() + "]",
                            NamedTextColor.AQUA
                    ),
                    line(
                            "새 해금: ",
                            formatValues(result.newlyUnlocked(), "없음"),
                            NamedTextColor.LIGHT_PURPLE
                    )
            );
            case BLOCKED -> List.of(
                    Component.text("승급 조건이 부족합니다.", NamedTextColor.RED),
                    line("부족한 수련: ", Long.toString(result.missingExp()), NamedTextColor.YELLOW),
                    line(
                            "부족한 조건: ",
                            formatValues(result.missingRequirements(), "없음"),
                            result.missingRequirements().isEmpty() ? NamedTextColor.GREEN : NamedTextColor.RED
                    )
            );
            case MAXIMUM_STAGE -> List.of(line(
                    "현재 최고 경지인 ",
                    "[" + result.currentStage().orElseThrow().displayName() + "]입니다.",
                    NamedTextColor.GOLD
            ));
            case IN_PROGRESS -> List.of(Component.text(
                    "이미 승급 요청을 처리 중입니다.",
                    NamedTextColor.YELLOW
            ));
        };
    }

    public List<Component> adminStage(RealmProgressView view, ProfileLoadState loadState) {
        return List.of(
                Component.text("MurimRPG 관리자 stage 조회", NamedTextColor.GOLD),
                line("Stage ID: ", view.currentStage().stageId(), NamedTextColor.AQUA),
                line("수련 EXP: ", Long.toString(view.profile().cultivationExp()), NamedTextColor.GREEN),
                line(
                        "완료 조건: ",
                        formatValues(view.profile().completedRequirements(), "없음"),
                        NamedTextColor.YELLOW
                ),
                line(
                        "해금 콘텐츠: ",
                        formatValues(view.profile().unlockedContent(), "없음"),
                        NamedTextColor.LIGHT_PURPLE
                ),
                line("캐시 로드 상태: ", loadState.displayName(), NamedTextColor.WHITE)
        );
    }

    public List<Component> debugProfile(
            RealmProgressView view,
            boolean cacheExisted,
            ProfileLoadState loadState
    ) {
        PlayerProfile profile = view.profile();
        return List.of(
                Component.text("MurimRPG 프로필 디버그", NamedTextColor.GOLD),
                line("UUID: ", profile.uuid().toString(), NamedTextColor.GRAY),
                line("캐시 존재: ", Boolean.toString(cacheExisted), NamedTextColor.WHITE),
                line("현재 stage ID: ", view.currentStage().stageId(), NamedTextColor.AQUA),
                line("대경지 ID: ", profile.majorRealm().id(), NamedTextColor.YELLOW),
                line("소경지 ID: ", profile.phase().id(), NamedTextColor.YELLOW),
                line("EXP: ", Long.toString(profile.cultivationExp()), NamedTextColor.GREEN),
                line("완료 조건: ", formatValues(profile.completedRequirements(), "없음"), NamedTextColor.YELLOW),
                line("해금 콘텐츠: ", formatValues(profile.unlockedContent(), "없음"), NamedTextColor.LIGHT_PURPLE),
                line("마지막 저장 시각: ", profile.updatedAt().toString(), NamedTextColor.WHITE),
                line("캐시 로드 상태: ", loadState.displayName(), NamedTextColor.WHITE)
        );
    }

    private Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text(label, NamedTextColor.GRAY)
                .append(Component.text(value, valueColor));
    }

    private String formatValues(java.util.Set<String> values, String emptyValue) {
        return values.isEmpty() ? emptyValue : values.stream().sorted().collect(java.util.stream.Collectors.joining(", "));
    }
}
