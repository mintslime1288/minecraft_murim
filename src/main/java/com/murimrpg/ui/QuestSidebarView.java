package com.murimrpg.ui;

import java.util.List;

public record QuestSidebarView(String title, List<String> lines, int progressPercent) {
    public QuestSidebarView {
        lines = List.copyOf(lines);
        if (progressPercent < 0 || progressPercent > 100) throw new IllegalArgumentException("Sidebar progress must be 0..100");
    }
}
