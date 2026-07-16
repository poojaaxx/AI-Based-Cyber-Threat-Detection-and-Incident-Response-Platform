package com.cyberguard.platform.entity.enums;

/**
 * Result of comparing Model A's (RandomForest) real-time classification
 * against Model B's (attention-LSTM temporal detector) classification of the
 * same event - see ThreatService.runCrossModelCheck(). UNAVAILABLE means
 * Model B could not be reached (e.g. its deployment doesn't have the NSL-KDD
 * dataset), not that the models disagreed.
 */
public enum CrossModelAgreement {
    AGREE, DISAGREE, UNAVAILABLE
}
