package com.murimrpg.domain;

import java.util.List;

/** Public command taxonomy. The command adapter consumes this without owning game rules. */
public final class GanghoCommandLayout {
    public static final List<String> ROOT_ALIASES = List.of("강호", "gangho", "murim");
    public static final List<String> CHARACTER_SHORTCUTS = List.of("내정보", "경지");
    public static final List<String> PROGRESSION_SHORTCUTS = List.of("문파", "무공", "내공", "운기조식");
    public static final List<String> GROUPS = List.of("정보", "문파", "무공", "모험", "경제", "관리");

    private GanghoCommandLayout() { }
}
