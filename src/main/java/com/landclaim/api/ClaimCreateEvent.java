package com.landclaim.api;

import com.landclaim.claim.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ClaimCreateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final Claim claim;
    private boolean cancelled;

    public ClaimCreateEvent(Player player, Claim claim) {
        this.player = player;
        this.claim = claim;
    }

    public Player getPlayer() { return player; }
    public Claim getClaim() { return claim; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
