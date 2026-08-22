package com.murimrpg.domain;

public record RequirementChange(String requirementId, boolean granted, boolean changed, PlayerProfile profile) {
}
