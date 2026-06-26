package com.landclaim.papi;

import com.landclaim.LandClaimPlugin;
import com.landclaim.claim.Claim;
import com.landclaim.claim.ClaimFlag;
import com.landclaim.claim.ClaimPermission;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class LandClaimExpansion extends PlaceholderExpansion {

    private final LandClaimPlugin plugin;

    public LandClaimExpansion(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "landclaim";
    }

    @Override
    public @NotNull String getAuthor() {
        return "LandClaimDev";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        UUID uuid = player.getUniqueId();
        Claim claim = plugin.getClaimManager().getClaim(uuid);

        return switch (params.toLowerCase()) {
            case "has_claim" -> String.valueOf(claim != null);
            case "claim_name" -> claim != null && claim.getName() != null ? claim.getName() : "";
            case "claim_tier" -> claim != null ? String.valueOf(claim.getTier()) : "0";
            case "claim_radius" -> claim != null ? String.valueOf(claim.getRadius()) : "0";
            case "claim_world" -> claim != null ? claim.getWorld().getName() : "";
            case "claim_center_x" -> claim != null ? String.valueOf(claim.getCenterX()) : "0";
            case "claim_center_z" -> claim != null ? String.valueOf(claim.getCenterZ()) : "0";
            case "claim_size" -> claim != null ? String.valueOf((claim.getRadius() * 2 + 1) * (claim.getRadius() * 2 + 1)) : "0";
            case "claim_border" -> plugin.getClaimBorder().isShowing(uuid) ? "enabled" : "disabled";
            case "claim_flag_pvp" -> getFlag(player, ClaimFlag.PVP);
            case "claim_flag_mobs" -> getFlag(player, ClaimFlag.MOB_SPAWNING);
            case "claim_flag_fire" -> getFlag(player, ClaimFlag.FIRE_SPREAD);
            case "claim_flag_explosions" -> getFlag(player, ClaimFlag.EXPLOSIONS);
            case "claims_count" -> String.valueOf(plugin.getClaimManager().getAllClaims().size());
            case "is_trusted" -> {
                String[] parts = params.split("_", 3);
                if (parts.length == 3) {
                    Player target = Bukkit.getPlayerExact(parts[2]);
                    if (target != null && claim != null) {
                        yield String.valueOf(claim.isTrusted(target.getUniqueId()));
                    }
                }
                yield "false";
            }
            case "in_claim" -> {
                if (player instanceof Player p) {
                    Claim at = plugin.getClaimManager().getClaimAt(p.getLocation());
                    yield String.valueOf(at != null);
                }
                yield "false";
            }
            case "claim_owner" -> {
                if (player instanceof Player p) {
                    Claim at = plugin.getClaimManager().getClaimAt(p.getLocation());
                    if (at != null) {
                        yield Bukkit.getOfflinePlayer(at.getOwner()).getName();
                    }
                }
                yield "";
            }
            default -> "";
        };
    }

    private String getFlag(OfflinePlayer player, ClaimFlag flag) {
        if (!(player instanceof Player p)) return "false";
        Claim at = plugin.getClaimManager().getClaimAt(p.getLocation());
        if (at == null) return "false";
        return String.valueOf(at.getFlag(flag));
    }
}
