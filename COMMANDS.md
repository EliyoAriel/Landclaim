# LandClaim Commands & Permissions

## Commands

Command: `/claim` | Aliases: `/landclaim`, `/lc`

| Command | Description | Permission |
|---|---|---|
| `/claim` | Create a claim at your location, or open GUI if you already have one | `landclaim.use` |
| `/claim gui` | Open the claim management GUI | `landclaim.use` |
| `/claim upgrade` | Upgrade claim to next tier (costs money via Vault) | `landclaim.upgrade` |
| `/claim info` | Show info about your claim or the claim you're standing in | `landclaim.use` |
| `/claim border` | Toggle claim border visualization (particles) | `landclaim.border` |
| `/claim trust <player> [build\|chest\|interact\|all]` | Trust a player in your claim (default: all) | `landclaim.trust` |
| `/claim untrust <player>` | Remove a trusted player from your claim | `landclaim.trust` |
| `/claim abandon` | Abandon your claim (confirmation: `/claim abandon confirm`) | `landclaim.use` |
| `/claim home` | Teleport to claim center or custom spawn (configurable cooldown) | `landclaim.use` |
| `/claim name <name>` | Set or change your claim name (max 100 chars) | `landclaim.use` |
| `/claim transfer <player>` | Transfer claim ownership to another player (requires confirmation) | `landclaim.use` |
| `/claim ban <player>` | Ban a player from your claim (requires confirmation) | `landclaim.use` |
| `/claim unban <player>` | Unban a player from your claim | `landclaim.use` |
| `/claim setspawn` | Set a custom teleport spawn point inside your claim | `landclaim.use` |
| `/claim top` | Show leaderboard of top 10 claims by radius | `landclaim.top` |
| `/claim logs` | Show last 20 actions on your claim | `landclaim.use` |
| `/claim notify` | Toggle claim enter/exit notifications | `landclaim.use` |
| `/claim admin` | Open the admin GUI (claim browser, global trust, blacklist, config, etc.) | `landclaim.admin` |
| `/claim admin bypass` | Toggle admin bypass mode (ignore all claim protection) | `landclaim.admin` |
| `/claim admin tax list` | List all delinquent claims with remaining grace time | `landclaim.admin` |
| `/claim admin tax forgive <player>` | Forgive taxes for a specific player's claim | `landclaim.admin` |
| `/claim admin remove <player>` | Force-remove another player's claim | `landclaim.admin` |

## Permissions

Set these via LuckPerms (or any permission plugin):

| Permission | Default | Description |
|---|---|---|
| `landclaim.use` | `true` (all players) | Access to `/claim` and all basic claim features (create, gui, info, abandon, home, name, transfer, ban, unban, setspawn, logs, notify) |
| `landclaim.upgrade` | `true` (all players) | Allows using `/claim upgrade` |
| `landclaim.border` | `true` (all players) | Allows using `/claim border` to see claim borders |
| `landclaim.trust` | `true` (all players) | Allows trusting/untrusting players via `/claim trust` and `/claim untrust` |
| `landclaim.top` | `true` (all players) | Allows viewing claim leaderboard via `/claim top` |
| `landclaim.admin` | `op` only | Admin access to all claims via `/claim admin`, `/claim admin bypass`, `/claim admin tax`, `/claim admin remove` |
| `landclaim.tier.<N>` | `false` | Override max claim tier (e.g. `landclaim.tier.5` allows up to tier 5) |
| `landclaim.starttier.<N>` | `false` | Start new claims at tier N (e.g. `landclaim.starttier.3`) |
| `landclaim.tax.exempt` | `false` | Exempt from automatic claim tax deductions |

## LuckPerms Examples

```bash
# Give all permissions to a player
/lp user <player> permission set landclaim.use true
/lp user <player> permission set landclaim.upgrade true
/lp user <player> permission set landclaim.border true
/lp user <player> permission set landclaim.trust true

# Give admin to a staff group
/lp group staff permission set landclaim.admin true

# Remove permission from a group
/lp group default permission set landclaim.upgrade false

# Create a permission group
/lp creategroup landclaim_user
/lp group landclaim_user permission set landclaim.use true
/lp group landclaim_user permission set landclaim.upgrade true
/lp group landclaim_user permission set landclaim.border true
/lp group landclaim_user permission set landclaim.trust true
/lp user <player> parent add landclaim_user
```
