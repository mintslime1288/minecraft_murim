package com.murimrpg.domain;

public record HuashanSettings(
        String organizationId, String entranceTrialId, String liuheQuestId,
        String secondPromotionTrialId, String firstPromotionTrialId,
        String trainingDummyId, String trainingDummyDisplayName,
        String examinerId, String examinerDisplayName, double examinerMaxHealth,
        int trialSpawnDelaySeconds, int meditationPreparationSeconds, int particleCount, int particleIntervalTicks,
        String particleType
) {
    public HuashanSettings {
        organizationId = ContentId.requireValid(organizationId, "Huashan organization ID");
        entranceTrialId = ContentId.requireValid(entranceTrialId, "Entrance trial ID");
        liuheQuestId = ContentId.requireValid(liuheQuestId, "Liuhe quest ID");
        secondPromotionTrialId = ContentId.requireValid(secondPromotionTrialId, "Second promotion trial ID");
        firstPromotionTrialId = ContentId.requireValid(firstPromotionTrialId, "First promotion trial ID");
        trainingDummyId = ContentId.requireValid(trainingDummyId, "Training dummy ID");
        if (trainingDummyDisplayName == null || trainingDummyDisplayName.isBlank())
            throw new IllegalArgumentException("Training dummy display name is blank");
        examinerId = ContentId.requireValid(examinerId, "Examiner ID");
        if (examinerDisplayName == null || examinerDisplayName.isBlank() || !Double.isFinite(examinerMaxHealth)
                || examinerMaxHealth <= 0)
            throw new IllegalArgumentException("Invalid examiner settings");
        if (trialSpawnDelaySeconds <= 0 || meditationPreparationSeconds <= 0 || particleCount < 0 || particleCount > 32
                || particleIntervalTicks <= 0 || particleIntervalTicks < 5) {
            throw new IllegalArgumentException("Invalid Huashan meditation/particle settings");
        }
        if (particleType == null || particleType.isBlank()) throw new IllegalArgumentException("Particle type is blank");
    }
}
