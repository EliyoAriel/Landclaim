package com.landclaim.api;

import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimPermission;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ClaimProtectionCheckEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Claim claim;
    private final ClaimPermission permission;
    private final ActionType actionType;
    private boolean cancelled;

    public enum ActionType {
        BLOCK_BREAK,
        BLOCK_PLACE,
        INTERACT_CONTAINER,
        INTERACT_SWITCH
    }

    public ClaimProtectionCheckEvent(Player player, Claim claim, ClaimPermission permission, ActionType actionType) {
        this.player = player;
        this.claim = claim;
        this.permission = permission;
        this.actionType = actionType;
    }

    public Player getPlayer() { return player; }
    public Claim getClaim() { return claim; }
    public ClaimPermission getRequiredPermission() { return permission; }
    public ActionType getActionType() { return actionType; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
