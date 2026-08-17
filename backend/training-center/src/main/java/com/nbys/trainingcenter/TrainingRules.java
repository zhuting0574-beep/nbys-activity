package com.nbys.trainingcenter;

final class TrainingRules {
    static final int MIN_TARGET_COUNT = 1;
    static final int MAX_TARGET_COUNT = 3;
    static final int ROOM_CAPACITY = 10;

    private TrainingRules() {}

    static int targetCount(int value) {
        return Math.max(MIN_TARGET_COUNT, Math.min(MAX_TARGET_COUNT, value));
    }

    static boolean roomIsFull(int memberCount) {
        return memberCount >= ROOM_CAPACITY;
    }
}
