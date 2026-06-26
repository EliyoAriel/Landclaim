package com.landclaim.claim;

public enum ClaimFlag {
    PVP(false),
    MOB_SPAWNING(true),
    FIRE_SPREAD(false),
    EXPLOSIONS(false),
    MOB_GRIEFING(false),
    CROP_TRAMPLE(false),
    PISTON_PROTECTION(true),
    MOB_DAMAGE(true),
    ANIMAL_DAMAGE(false),
    VEHICLE_DAMAGE(false);

    private final boolean defaultValue;

    ClaimFlag(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}
