package com.landclaim.api;

import com.landclaim.claim.Claim;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ClaimDeleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID owner;
    private final Claim claim;
    private final DeleteReason reason;

    public enum DeleteReason {
        ABANDON,
        ADMIN_REMOVE
    }

    public ClaimDeleteEvent(UUID owner, Claim claim, DeleteReason reason) {
        this.owner = owner;
        this.claim = claim;
        this.reason = reason;
    }

    public UUID getOwner() { return owner; }
    public Claim getClaim() { return claim; }
    public DeleteReason getReason() { return reason; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
