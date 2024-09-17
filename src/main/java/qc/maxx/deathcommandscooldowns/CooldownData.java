package qc.maxx.deathcommandscooldowns;

import java.util.UUID;

public class CooldownData {
    private UUID uuid;
    private String deathWorld;
    private long endTime;

    public CooldownData(UUID uuid, String deathWorld, long endTime) {
        this.uuid = uuid;
        this.deathWorld = deathWorld;
        this.endTime = endTime;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getDeathWorld() {
        return deathWorld;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public double getRemainingTime() {
        return (endTime - System.currentTimeMillis()) / 1000.0D;
    }

    @Override
    public String toString() {
        return "UUID " + uuid + " DeathWorld " + deathWorld + " EndTime " + endTime + " RemainingTime " + getRemainingTime();
    }
}
