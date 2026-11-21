package com.extantape.bluemapmini.share;

import com.extantape.bluemapmini.BlueMapMiniPlugin;
import com.extantape.bluemapmini.api.CoordinateShareEvent;
import com.extantape.bluemapmini.config.HudConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shares a player's coordinates with people they choose. Nothing is sent without the sender asking, and
 * players can turn receiving off. Other plugins can narrow or stop any share through {@link CoordinateShareEvent}.
 */
public final class ShareService {
    public static final String SEND = "bluemapmini.share.send";
    public static final String RECEIVE = "bluemapmini.share.receive";

    private final BlueMapMiniPlugin plugin;
    /** When each player last shared. Kept across rejoins, so leaving can't skip the cooldown. */
    private final Map<UUID, Long> lastShare = new HashMap<>();

    public ShareService(BlueMapMiniPlugin plugin) {
        this.plugin = plugin;
    }

    private HudConfig.Share config() {
        return plugin.config().share();
    }

    /** A world's group: its name without a trailing {@code _nether} or {@code _the_end}, as chat plugins count it. */
    public static String worldGroup(String world) {
        if (world.endsWith("_nether")) return world.substring(0, world.length() - "_nether".length());
        if (world.endsWith("_the_end")) return world.substring(0, world.length() - "_the_end".length());
        return world;
    }

    /**
     * Whether a player in {@code otherWorld} can be offered a share from {@code senderWorld} under this audience
     * (D254). Mayor's mode offers everyone and leaves narrowing to Mayor through the share event; off offers nobody.
     */
    static boolean offered(HudConfig.ShareAudience audience, String senderWorld, String otherWorld) {
        return switch (audience) {
            case OFF -> false;
            case WORLD -> senderWorld.equals(otherWorld);
            case WORLD_GROUP, MAYOR_WORLD_GROUP -> worldGroup(senderWorld).equals(worldGroup(otherWorld));
            case MAYOR, SERVER -> true;
        };
    }

    /** Players the sender can offer their coordinates to, by name. */
    public List<Player> candidates(Player sender) {
        HudConfig.ShareAudience audience = config().audience();
        String here = sender.getWorld().getName();
        List<Player> result = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(sender) || !sender.canSee(other)) continue;
            if (!com.extantape.bluemapmini.Perms.has(other, RECEIVE) || !plugin.hud().session(other).settings().receiveShares) continue;
            if (!offered(audience, here, other.getWorld().getName())) continue;
            result.add(other);
        }
        result.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        return result;
    }

    /** An online player the sender can see, by name in any case, or null. */
    public static Player findVisible(Player sender, String name) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getName().equalsIgnoreCase(name) && sender.canSee(other)) return other;
        }
        return null;
    }

    /** Why the sender can't share right now, or null if they can. */
    public String blocked(Player sender) {
        if (config().audience() == HudConfig.ShareAudience.OFF) return "Sharing coordinates is turned off on this server.";
        if (!com.extantape.bluemapmini.Perms.has(sender, SEND)) return "You can't share your coordinates here.";
        String guard = olderMayor();
        if (guard != null) {
            plugin.getLogger().warning(guard);
            return "Sharing coordinates is off on this server for now. An admin has been told why.";
        }
        Long last = lastShare.get(sender.getUniqueId());
        long wait = last == null ? 0 : last + config().cooldownSeconds() * 1000L - System.currentTimeMillis();
        if (wait > 0) {
            long seconds = (wait + 999) / 1000;
            return "You can share again in " + seconds + (seconds == 1 ? " second." : " seconds.");
        }
        return null;
    }

    /**
     * Shares the sender's current coordinates with {@code chosen}, or with everyone they can share with when
     * {@code chosen} is null. Tells the sender exactly who received them. Must run on the main thread.
     */
    public void share(Player sender, Collection<Player> chosen) {
        String blocked = blocked(sender);
        if (blocked != null) {
            sender.sendMessage(Component.text(blocked, NamedTextColor.RED));
            return;
        }
        List<Player> offered = candidates(sender);
        Set<Player> recipients = new LinkedHashSet<>();
        List<String> unreachable = new ArrayList<>();
        if (chosen == null) {
            recipients.addAll(offered);
        } else {
            for (Player player : chosen) {
                if (offered.contains(player)) recipients.add(player);
                else if (!player.equals(sender)) unreachable.add(player.getName());
            }
        }
        if (recipients.isEmpty()) {
            sender.sendMessage(Component.text(chosen == null
                    ? "There's nobody you can share your coordinates with right now."
                    : "Nobody you picked can receive shared coordinates right now.", NamedTextColor.YELLOW));
            return;
        }

        Location at = sender.getLocation();
        World world = at.getWorld();
        int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
        // The event's set may be changed by other plugins, so remember who was offered first.
        List<Player> offeredToEvent = new ArrayList<>(recipients);
        CoordinateShareEvent event = new CoordinateShareEvent(sender, world, x, y, z, recipients);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            sender.sendMessage(Component.text("Your coordinates weren't sent.", NamedTextColor.YELLOW));
            return;
        }
        List<Player> received = new ArrayList<>();
        for (Player player : event.getRecipients()) {
            if (player.isOnline() && !player.equals(sender)) received.add(player);
        }
        for (Player player : offeredToEvent) {
            if (!received.contains(player)) unreachable.add(player.getName());
        }
        if (received.isEmpty()) {
            sender.sendMessage(Component.text("Nobody could receive your coordinates.", NamedTextColor.YELLOW));
            return;
        }

        String coords = x + " " + y + " " + z;
        Component message = Component.text(sender.getName(), NamedTextColor.AQUA)
                .append(Component.text(" shared where they are: ", NamedTextColor.GRAY))
                .append(Component.text(coords, NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.copyToClipboard(coords))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy"))))
                .append(Component.text(" in " + place(world), NamedTextColor.GRAY));
        for (Player player : received) player.sendMessage(message);

        lastShare.put(sender.getUniqueId(), System.currentTimeMillis());
        List<String> names = received.stream().map(Player::getName).toList();
        sender.sendMessage(Component.text("Sent your coordinates (" + coords + ") to " + names.size()
                + (names.size() == 1 ? " player: " : " players: ") + String.join(", ", names) + ".", NamedTextColor.GRAY));
        if (!unreachable.isEmpty()) {
            sender.sendMessage(Component.text("Not sent to " + String.join(", ", unreachable)
                    + ": they can't receive shared coordinates right now.", NamedTextColor.YELLOW));
        }
        if (config().log()) {
            plugin.getLogger().info(sender.getName() + " shared " + coords + " in " + world.getName() + " with " + String.join(", ", names));
        }
    }

    /** Where the coordinates are, by dimension, so world names that might mean something private aren't shown. */
    private static String place(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> "the Overworld";
            case NETHER -> "the Nether";
            case THE_END -> "the End";
            default -> world.getName();
        };
    }

    /**
     * In Mayor's modes, shares follow Mayor's player groups, so they're off (fail closed) unless Mayor 0.5.0 or newer
     * is running. In every mode, a Mayor older than 0.5.0 turns sharing off, because it listens for a share event
     * BlueMapMini doesn't send and would let shares skip its groups. The version is null when Mayor isn't running.
     * Returns why sharing is off, or null.
     */
    static String mayorGuard(HudConfig.ShareAudience audience, String version) {
        boolean needsMayor = audience == HudConfig.ShareAudience.MAYOR || audience == HudConfig.ShareAudience.MAYOR_WORLD_GROUP;
        if (version == null && !needsMayor) return null;
        if (version == null) return "Coordinate sharing is off: share.audience follows Mayor's player groups, and Mayor isn't running. Install Mayor 0.5.0 or newer, or pick another share.audience.";
        String[] p = version.split("[.-]");
        try {
            int major = Integer.parseInt(p[0]), minor = p.length > 1 ? Integer.parseInt(p[1]) : 0;
            if (major > 0 || minor >= 5) return null;
        } catch (NumberFormatException e) {
            // Unreadable version: treat it as too old.
        }
        return "Coordinate sharing is off because Mayor " + version + " can't see BlueMapMini's shares, so they'd skip its player groups. Update Mayor to 0.5.0 or newer.";
    }

    private String olderMayor() {
        org.bukkit.plugin.Plugin mayor = plugin.getServer().getPluginManager().getPlugin("Mayor");
        boolean on = mayor != null && mayor.isEnabled();
        return mayorGuard(config().audience(), on ? mayor.getPluginMeta().getVersion() : null);
    }
}
