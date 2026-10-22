package com.olafreset.service;

/**
 * 累計 XP から求めたレベル。
 * レベル n から n+1 に上がるには n × 100 XP が必要。
 */
public record Level(int level, long xpIntoLevel, long xpForNextLevel) {

    public static Level fromTotalXp(long totalXp) {
        int level = 1;
        long remaining = totalXp;
        while (remaining >= level * 100L) {
            remaining -= level * 100L;
            level++;
        }
        return new Level(level, remaining, level * 100L);
    }

    public int progressPercent() {
        return (int) (xpIntoLevel * 100 / xpForNextLevel);
    }
}
