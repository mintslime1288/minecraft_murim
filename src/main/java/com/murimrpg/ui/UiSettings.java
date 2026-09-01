package com.murimrpg.ui;

import com.murimrpg.infrastructure.config.YamlDocument;
import java.nio.file.Path;

public record UiSettings(int refreshTicks, int transientMessageTicks) {
    public UiSettings {
        if (refreshTicks < 5 || refreshTicks > 40) throw new IllegalArgumentException("ui.yml.hud.refresh-ticks must be 5..40");
        if (transientMessageTicks < 10 || transientMessageTicks > 200) throw new IllegalArgumentException("ui.yml.hud.transient-message-ticks must be 10..200");
    }
    public static UiSettings load(Path path) {
        YamlDocument yaml = YamlDocument.load(path);
        return new UiSettings(yaml.integer("hud.refresh-ticks", 10), yaml.integer("hud.transient-message-ticks", 40));
    }
}
