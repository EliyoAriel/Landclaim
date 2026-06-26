package com.landclaim.api;

import com.landclaim.claim.Claim;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ClaimTransferEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Player fromPlayer;
    private final UUID from;
    private final UUID to;
    private final Claim claim;
    private boolean cancelled;

    public ClaimTransferEvent(Player fromPlayer, UUID from, UUID to, Claim claim) {
        this.fromPlayer = fromPlayer;
        this.from = from;
        this.to = to;
        this.claim = claim;
    }

    public Player getFromPlayer() { return fromPlayer; }
    public UUID getFrom() { return from; }
    public UUID getTo() { return to; }
    public Claim getClaim() { return claim; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
