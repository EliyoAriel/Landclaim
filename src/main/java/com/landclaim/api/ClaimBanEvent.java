package com.landclaim.api;

import com.landclaim.claim.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ClaimBanEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player owner;
    private final UUID target;
    private final Claim claim;
    private boolean cancelled;

    public ClaimBanEvent(Player owner, UUID target, Claim claim) {
        this.owner = owner;
        this.target = target;
        this.claim = claim;
    }

    public Player getOwner() { return owner; }
    public UUID getTarget() { return target; }
    public Claim getClaim() { return claim; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
