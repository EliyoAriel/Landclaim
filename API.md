# LandClaim API

Package: `com.landclaim.api`

## Dependency

Add LandClaim as a dependency in your `plugin.yml`:

```yaml
depend: [LandClaim]
```

### Maven (if LandClaim is installed in your local repo)

```xml
<dependency>
    <groupId>com.landclaim</groupId>
    <artifactId>LandClaim</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## Events

All events extend `org.bukkit.event.Event`. Cancellable events implement `org.bukkit.event.Cancellable`.

### ClaimProtectionCheckEvent

Fired before LandClaim blocks a block break, block place, or interact action inside a claim.

- **Cancellable:** yes
- **When cancelled:** LandClaim skips its own protection check for this action (other plugins are expected to handle it).

```java
@EventHandler
public void onProtectionCheck(ClaimProtectionCheckEvent event) {
    Player p = event.getPlayer();
    Claim c = event.getClaim();

    // Allow building in specific world
    if (event.getActionType() == ClaimProtectionCheckEvent.ActionType.BLOCK_PLACE
            && p.getWorld().getName().equals("buildworld")) {
        event.setCancelled(true);
    }
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getPlayer()` | `Player` | The player attempting the action |
| `getClaim()` | `Claim` | The claim being checked |
| `getRequiredPermission()` | `ClaimPermission` | Permission needed: `BUILD`, `CHEST`, or `INTERACT` |
| `getActionType()` | `ActionType` | Enum: `BLOCK_BREAK`, `BLOCK_PLACE`, `INTERACT_CONTAINER`, `INTERACT_SWITCH` |

---

### ClaimCreateEvent

Fired when a player creates a new claim.

- **Cancellable:** yes
- **When cancelled:** The claim is not created.

```java
@EventHandler
public void onCreate(ClaimCreateEvent event) {
    // Prevent claims too close to spawn
    if (event.getClaim().getCenterX() == 0 && event.getClaim().getCenterZ() == 0) {
        event.setCancelled(true);
        event.getPlayer().sendMessage("Cannot claim at spawn!");
    }
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getPlayer()` | `Player` | The player creating the claim |
| `getClaim()` | `Claim` | The claim about to be created |

---

### ClaimDeleteEvent

Fired after a claim is abandoned or removed by an admin.

- **Not cancellable** — notification only.

```java
@EventHandler
public void onDelete(ClaimDeleteEvent event) {
    getLogger().info("Claim deleted: " + event.getClaim().getCenterX() + "," + event.getClaim().getCenterZ()
            + " reason=" + event.getReason());
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getOwner()` | `UUID` | UUID of the former owner |
| `getClaim()` | `Claim` | The claim being deleted |
| `getReason()` | `DeleteReason` | `ABANDON` or `ADMIN_REMOVE` |

---

### ClaimTransferEvent

Fired when a claim is transferred to another player.

- **Cancellable:** yes
- **When cancelled:** The transfer is aborted.

```java
@EventHandler
public void onTransfer(ClaimTransferEvent event) {
    // Prevent transfer to certain players
    if (event.getTo().equals(someBlacklistedUUID)) {
        event.setCancelled(true);
    }
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getFromPlayer()` | `Player` | The original owner (may be null if offline) |
| `getFrom()` | `UUID` | UUID of the current owner |
| `getTo()` | `UUID` | UUID of the new owner |
| `getClaim()` | `Claim` | The claim being transferred |

---

### ClaimTrustEvent

Fired when a player is trusted in a claim.

- **Cancellable:** yes
- **When cancelled:** The player is not trusted.

```java
@EventHandler
public void onTrust(ClaimTrustEvent event) {
    // Log to external database
    logTrust(event.getOwner().getName(), event.getTarget(), event.getPermissions());
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getOwner()` | `Player` | The claim owner |
| `getTarget()` | `UUID` | UUID of the player being trusted |
| `getClaim()` | `Claim` | The claim |
| `getPermissions()` | `Set<ClaimPermission>` | The permissions granted |

---

### ClaimUntrustEvent

Fired when a player is untrusted from a claim.

- **Cancellable:** yes
- **When cancelled:** The player remains trusted.

```java
@EventHandler
public void onUntrust(ClaimUntrustEvent event) {
    if (protectedPlayers.contains(event.getTarget())) {
        event.setCancelled(true);
    }
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getOwner()` | `Player` | The claim owner |
| `getTarget()` | `UUID` | UUID of the player being untrusted |
| `getClaim()` | `Claim` | The claim |

---

### ClaimBanEvent

Fired when a player is banned from a claim.

- **Cancellable:** yes
- **When cancelled:** The player is not banned.

```java
@EventHandler
public void onBan(ClaimBanEvent event) {
    // Prevent staff from being banned
    OfflinePlayer target = Bukkit.getOfflinePlayer(event.getTarget());
    if (target.isOnline() && target.getPlayer().hasPermission("landclaim.bypass")) {
        event.setCancelled(true);
    }
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getOwner()` | `Player` | The claim owner |
| `getTarget()` | `UUID` | UUID of the player being banned |
| `getClaim()` | `Claim` | The claim |

---

### ClaimUnbanEvent

Fired when a player is unbanned from a claim.

- **Cancellable:** yes
- **When cancelled:** The player remains banned.

```java
@EventHandler
public void onUnban(ClaimUnbanEvent event) {
    getLogger().info(event.getOwner().getName() + " unbanned " + event.getTarget());
}
```

**Getters:**

| Method | Returns | Description |
|---|---|---|
| `getOwner()` | `Player` | The claim owner |
| `getTarget()` | `UUID` | UUID of the player being unbanned |
| `getClaim()` | `Claim` | The claim |

---

## Public API Classes

These classes from `com.landclaim.claim` and `com.landclaim` can also be accessed by other plugins:

### LandClaimPlugin (main class)

```java
LandClaimPlugin plugin = (LandClaimPlugin) Bukkit.getPluginManager().getPlugin("LandClaim");
```

| Method | Returns | Description |
|---|---|---|---|
| `getInstance()` | `LandClaimPlugin` | Static singleton |
| `getClaimManager()` | `ClaimManager` | Claim CRUD + queries |
| `getEconomyManager()` | `EconomyManager` | Vault economy integration |
| `getClaimBorder()` | `ClaimBorder` | Border visualization |
| `getMsg()` | `MessageManager` | Message manager (loaded from messages.yml) |
| `getDatabase()` | `DatabaseManager` | Database access |
| `getHomeCooldowns()` | `Map<UUID, Long>` | Home teleport cooldown map |
| `setPendingInput(UUID, String)` | `void` | Set a pending chat input type for a player |
| `getPendingInput(UUID)` | `String` | Get pending chat input type |
| `isAdminBypassDisabled(UUID)` | `boolean` | Whether admin bypass is toggled off for a player |
| `toggleAdminBypass(UUID)` | `boolean` | Toggle bypass mode (returns new state) |
| `reload()` | `void` | Reload config, messages, and claims from database |

### Claim (model)

```java
Claim claim = plugin.getClaimManager().getClaimAt(location);
```

| Method | Returns | Description |
|---|---|---|---|
| `getOwner()` | `UUID` | Claim owner UUID |
| `getWorld()` | `World` | Claim world |
| `getCenterX()` | `int` | Center X (block) |
| `getCenterZ()` | `int` | Center Z (block) |
| `getRadius()` | `int` | Claim radius |
| `getTier()` | `int` | Upgrade tier |
| `getName()` | `String` | Claim name (nullable) |
| `contains(Location)` | `boolean` | Whether location is inside claim |
| `getAllowedPlayers()` | `Map<UUID, EnumSet<ClaimPermission>>` | Trusted players map |
| `getBannedPlayers()` | `Set<UUID>` | Banned players set |
| `isPlayerAllowed(UUID, ClaimPermission)` | `boolean` | Permission check |
| `isBanned(UUID)` | `boolean` | Ban check |
| `isTrusted(UUID)` | `boolean` | Whether player is in the trusted list |
| `getPermissions(UUID)` | `EnumSet<ClaimPermission>` | Permissions for a trusted player |
| `setPermissions(UUID, Set<ClaimPermission>)` | `void` | Update permissions for a trusted player |
| `getFlag(ClaimFlag)` | `boolean` | Flag value |
| `setFlag(ClaimFlag, boolean)` | `void` | Set flag value |
| `getFlags()` | `Map<ClaimFlag, Boolean>` | All flag values |
| `ban(UUID)` | `void` | Ban a player from claim |
| `unban(UUID)` | `void` | Unban a player from claim |
| `setRadius(int)` | `void` | Set claim radius |
| `setTier(int)` | `void` | Set claim tier |
| `setName(String)` | `void` | Set claim name |
| `hasCustomSpawn()` | `boolean` | Whether a custom spawn is set |
| `getSpawnLocation()` | `Location` | Custom spawn location (nullable) |
| `setSpawnLocation(Location)` | `void` | Set custom spawn location |
| `getTaxNextDue()` | `long` | Timestamp (ms) of next tax due |
| `setTaxNextDue(long)` | `void` | Set next tax due timestamp |
| `getTaxGraceEnd()` | `long` | Timestamp (ms) when grace period ends |
| `setTaxGraceEnd(long)` | `void` | Set grace period end timestamp |
| `isTaxDelinquent()` | `boolean` | Whether claim is past grace and due for removal |

### ClaimManager

```java
ClaimManager cm = plugin.getClaimManager();
```

| Method | Returns | Description |
|---|---|---|
| `getClaim(UUID)` | `Claim` | Claim by owner |
| `getClaimAt(Location)` | `Claim` | Claim at location |
| `getAllClaims()` | `Collection<Claim>` | All loaded claims |
| `createClaim(UUID, Location)` | `Claim` | Create a new claim |
| `removeClaim(UUID)` | `void` | Remove a claim |
| `transferClaim(UUID, UUID)` | `void` | Transfer claim from one owner to another |
| `canUpgrade(UUID)` | `boolean` | Whether a player can upgrade their claim |
| `canUpgrade(Player)` | `boolean` | Whether a player can upgrade (respects permission tiers) |
| `getUpgradeCost(UUID)` | `int` | Cost to upgrade to next tier |
| `getUpgradeCost(Player)` | `int` | Cost to upgrade for a player |
| `upgrade(Player)` | `boolean` | Attempt to upgrade claim |
| `collidesWithAny(Claim)` | `boolean` | Whether a claim would overlap any existing claim |
| `isBlacklisted(Location)` | `boolean` | Whether location is in a blacklisted world/area |
| `getBlacklistWorlds()` | `List<String>` | Blacklisted world names |
| `addBlacklistWorld(String)` | `void` | Add a world to blacklist |
| `removeBlacklistWorld(String)` | `void` | Remove a world from blacklist |
| `getBlacklistAreas()` | `List<Map<?, ?>>` | Blacklisted area definitions |
| `addBlacklistArea(String, int, int, int, int)` | `void` | Add an area to blacklist |
| `removeBlacklistArea(int)` | `void` | Remove blacklisted area by index |
| `isGloballyTrusted(UUID)` | `boolean` | Global trust check |
| `addGlobalTrust(UUID)` | `void` | Add a player to global trust |
| `removeGlobalTrust(UUID)` | `void` | Remove a player from global trust |
| `getGlobalTrustedPlayers()` | `Set<UUID>` | All globally trusted players |
| `isNotifyEnabled(UUID)` | `boolean` | Whether notifications are enabled for a player |
| `toggleNotify(UUID)` | `boolean` | Toggle notifications (returns new state) |
| `getPermissionMaxTier(Player)` | `int` | Max tier from `landclaim.tier.X` permission |
| `getPermissionStartTier(Player)` | `int` | Starting tier from `landclaim.starttier.X` permission |
| `processTax()` | `void` | Run one tax collection cycle for all claims |
| `logClaimEvent(String, String, String, String)` | `void` | Log a claim event to database |
| `getClaimLogs(String, int)` | `List<Map<String, Object>>` | Recent claim logs for an owner |
| `saveAll()` | `void` | Save all claims and global trust to database |
| `loadAll()` | `void` | Load all data from database |

### ClaimPermission (enum)

| Value | Description |
|---|---|
| `BUILD` | Place and break blocks |
| `CHEST` | Open containers (chests, barrels, etc.) |
| `INTERACT` | Use switches, doors, buttons, etc. |

### ClaimFlag (enum)

| Flag | Default | Description |
|---|---|---|
| `PVP` | `false` | Allow PvP in claim |
| `MOB_SPAWNING` | `true` | Allow natural mob spawning |
| `FIRE_SPREAD` | `false` | Allow fire spread |
| `EXPLOSIONS` | `false` | Allow explosions (TNT, creeper, etc.) |
| `MOB_GRIEFING` | `false` | Allow mob griefing (creeper, enderman, etc.) |
| `CROP_TRAMPLE` | `false` | Allow crop trampling |
| `PISTON_PROTECTION` | `true` | Protect pistons from being moved into claim |
| `MOB_DAMAGE` | `true` | Allow mobs to deal damage inside claim |
| `ANIMAL_DAMAGE` | `false` | Allow animal damage inside claim |
| `VEHICLE_DAMAGE` | `false` | Allow vehicle (boat, minecart) damage inside claim |

### ClaimDeleteEvent.DeleteReason (enum)

| Value | Description |
|---|---|
| `ABANDON` | Owner abandoned the claim via `/claim abandon` |
| `ADMIN_REMOVE` | Admin force-removed the claim via `/claim admin remove` |

---

## PlaceholderAPI Placeholders

When PlaceholderAPI is installed, LandClaim registers the `%landclaim_*%` placeholders:

| Placeholder | Returns | Description |
|---|---|---|
| `%landclaim_has_claim%` | `true`/`false` | Whether the player has a claim |
| `%landclaim_claim_name%` | `String` | Player's claim name (empty if none) |
| `%landclaim_claim_tier%` | `int` | Player's claim tier (`0` if none) |
| `%landclaim_claim_radius%` | `int` | Player's claim radius (`0` if none) |
| `%landclaim_claim_world%` | `String` | World of player's claim |
| `%landclaim_claim_center_x%` | `int` | Center X of player's claim |
| `%landclaim_claim_center_z%` | `int` | Center Z of player's claim |
| `%landclaim_claim_size%` | `int` | Total block area of player's claim |
| `%landclaim_claim_border%` | `enabled`/`disabled` | Whether border visualization is active |
| `%landclaim_claim_flag_pvp%` | `true`/`false` | PvP flag at player's location |
| `%landclaim_claim_flag_mobs%` | `true`/`false` | Mob spawning flag at player's location |
| `%landclaim_claim_flag_fire%` | `true`/`false` | Fire spread flag at player's location |
| `%landclaim_claim_flag_explosions%` | `true`/`false` | Explosions flag at player's location |
| `%landclaim_claims_count%` | `int` | Total number of claims on the server |
| `%landclaim_in_claim%` | `true`/`false` | Whether the player is inside any claim |
| `%landclaim_claim_owner%` | `String` | Name of the owner of the claim the player is in |
| `%landclaim_is_trusted_<name>%` | `true`/`false` | Whether the named player is trusted in the player's claim |
