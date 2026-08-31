package com.murimrpg.service;

import com.murimrpg.domain.PlayerJourney;

public record HuashanResult(PlayerJourney state, String message, boolean activityCompleted) {}
