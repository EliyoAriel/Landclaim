package com.landclaim.api;

import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimPermission;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

public class ClaimTrustEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player owner;
    private final UUID target;
    private final Claim claim;
    private final Set<ClaimPermission> permissions;
    private boolean cancelled;

    public ClaimTrustEvent(Player owner, UUID target, Claim claim, Set<ClaimPermission> permissions) {
        this.owner = owner;
        this.target = target;
        this.claim = claim;
        this.permissions = permissions;
    }

    public Player getOwner() { return owner; }
    public UUID getTarget() { return target; }
    public Claim getClaim() { return claim; }
    public Set<ClaimPermission> getPermissions() { return permissions; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
