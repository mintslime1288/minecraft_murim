package com.murimrpg;

import com.murimrpg.domain.ContentCatalog;
import com.murimrpg.domain.CultivationProgression;
import com.murimrpg.service.CharacterCreationService;
import com.murimrpg.service.HuashanService;
import com.murimrpg.service.PlayerContentService;
import com.murimrpg.service.PlayerProfileService;
import com.murimrpg.service.ProgressionService;
import com.murimrpg.scheduler.ServerTickScheduler;
import com.murimrpg.cultivation.MeditationController;
import com.murimrpg.ui.FabricUiManager;

public record GanghoContext(PlayerProfileService profiles, PlayerContentService content,
                            ProgressionService progressionService, CultivationProgression progression,
                            ContentCatalog catalog, HuashanService huashan,
                            CharacterCreationService characters, ServerTickScheduler scheduler,
                            MeditationController meditation, FabricUiManager ui) { }
