package com.darkniightz.core.system;

import com.darkniightz.core.permissions.PermissionConstants;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.core.world.WorldManager;
import com.darkniightz.main.JebaitedCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ServerScoreboardManager {
    private final Plugin plugin;
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final WorldManager worldManager;
    private BukkitTask task;
    private int frame;
    private volatile int discordLinkedCount = -1;
    private volatile long lastDiscordLinkedRefreshMs = 0L;
    private volatile boolean discordRefreshInFlight;

    public ServerScoreboardManager(Plugin plugin, ProfileStore profiles, RankManager ranks, WorldManager worldManager) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.ranks = ranks;
        this.worldManager = worldManager;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }
        long interval = Math.max(20L, plugin.getConfig().getLong("scoreboard.update_ticks", 40L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        frame++;
        refreshDiscordLinkedCountAsync();
        if (Bukkit.getScoreboardManager() != null) {
            applyNametagTeams(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshPlayer(player);
        }
    }

    public void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            clear(player);
            return;
        }

        PlayerProfile profile = profiles.get(player.getUniqueId());
        String mode = profile == null ? "normal" : profile.getScoreboardMode();
        if (profile != null && !profile.isScoreboardVisible()) {
            clear(player);
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getNewScoreboard() : null;
        if (board == null) {
            return;
        }

        Objective objective = board.registerNewObjective("jebaited", "dummy", animatedTitle());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        applyNametagTeams(board);

        String domain = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("scoreboard.footer", "&fplay.jebaited.net"));
        int online = Bukkit.getOnlinePlayers().size();
        String rank = profile != null ? prettyRank(profile.getPrimaryRank()) : prettyRank(ranks.getDefaultGroup());
        String worldTag = worldManager.getWorldLabel(player);
        boolean hub = worldManager.isHub(player);
        String resourceLabel = hub ? "Coins" : "Balance";
        String resourceValue = hub
                ? "§6" + compactNumber(profile != null ? profile.getCosmeticCoins() : 0L)
                : "§a$" + compactNumber(profile != null ? profile.getBalance() : 0D);

        int score = "minimal".equalsIgnoreCase(mode) ? 8 : 11;
        if (!"minimal".equalsIgnoreCase(mode)) {
            objective.getScore("§fName: §b" + player.getName()).setScore(score--);
            objective.getScore("§fRank: §e" + rank).setScore(score--);
            objective.getScore("§0").setScore(score--);
        }

        objective.getScore("§fWorld: §a" + worldTag).setScore(score--);
        if (plugin instanceof com.darkniightz.main.JebaitedCore jc && jc.getEventModeManager() != null) {
            java.util.List<String> eventLines = jc.getEventModeManager().getEventScoreboardLines();
            java.util.List<String> chatLines = jc.getEventModeManager().getChatGameScoreboardLines();
            if ((eventLines != null && !eventLines.isEmpty()) || (chatLines != null && !chatLines.isEmpty())) {
                objective.getScore("§3").setScore(score--);
                int ex = 0;
                if (eventLines != null) {
                    for (String line : eventLines) {
                        if (score <= 1) break;
                        ChatColor pad = ChatColor.values()[ex++ % ChatColor.values().length];
                        String unique = line + pad + ChatColor.RESET;
                        if (unique.length() > 64) {
                            unique = unique.substring(0, 64);
                        }
                        objective.getScore(unique).setScore(score--);
                    }
                }
                if (chatLines != null) {
                    for (String line : chatLines) {
                        if (score <= 1) break;
                        ChatColor pad = ChatColor.values()[ex++ % ChatColor.values().length];
                        String unique = line + pad + ChatColor.RESET;
                        if (unique.length() > 64) {
                            unique = unique.substring(0, 64);
                        }
                        objective.getScore(unique).setScore(score--);
                    }
                }
            }
        }
        objective.getScore("§fPlayers: §a" + online).setScore(score--);
        objective.getScore("§1").setScore(score--);
        objective.getScore("§f" + resourceLabel + ": " + resourceValue).setScore(score--);
        if (!"minimal".equalsIgnoreCase(mode)) {
            objective.getScore("§2").setScore(score--);
            objective.getScore(domain).setScore(score);
        }

        player.setScoreboard(board);
        applyTablist(player);
    }

    public void clear(Player player) {
        if (player == null || Bukkit.getScoreboardManager() == null) {
            return;
        }
        Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        applyNametagTeams(main);
        player.setScoreboard(main);
    }

    private void applyNametagTeams(Scoreboard board) {
        if (board == null) {
            return;
        }
        for (Player target : Bukkit.getOnlinePlayers()) {
            PlayerProfile profile = profiles.get(target.getUniqueId());
            String rank = profile == null ? ranks.getDefaultGroup() : profile.getPrimaryRank();
            RankManager.RankStyle style = ranks.getStyle(rank);
            String teamName = ("jb" + Math.abs(target.getEntityId()));
            if (teamName.length() > 16) {
                teamName = teamName.substring(0, 16);
            }
            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            team.addEntry(target.getName());
            boolean show = profile == null || profile.isHeadNametagsEnabled();
            boolean extra = profile != null && profile.isNametagExtraEnabled();
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, show ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);
            team.setColor(resolveColor(style));
            team.setPrefix(show ? safeNametagText(cleanPrefix(style.prefix)) : "");
            team.setSuffix(show && extra ? safeNametagText(nametagSuffix(target, profile)) : "");
        }
    }

    private String cleanPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix + " ";
    }

    private String nametagSuffix(Player player, PlayerProfile profile) {
        if (player == null || player.getWorld() == null) {
            return "";
        }
        String worldTag = worldManager.getWorldLabel(player);
        String activeTag = resolveActiveTag(profile);
        String equipped = resolveEquipped(profile);
        String balance = profile == null ? "$0" : compactNumber(profile.getBalance());
        String coins = profile == null ? "0" : Integer.toString(profile.getCosmeticCoins());

        String fallback = !activeTag.isBlank()
                ? " §8• §d" + activeTag
                : " §8• §6$" + balance;
        String template = plugin.getConfig().getString("scoreboard.nametag_extra_format", fallback);
        if (template == null || template.isBlank()) {
            return fallback;
        }

        String rendered = template
                .replace("{world}", worldTag)
                .replace("{tag}", activeTag)
                .replace("{equipped}", equipped)
                .replace("{balance}", "$" + balance)
                .replace("{coins}", coins);

        String stripped = ChatColor.stripColor(rendered).replace("•", "").trim();
        return stripped.isEmpty() ? fallback : rendered;
    }

    private String safeNametagText(String value) {
        if (value == null) {
            return "";
        }
        int max = Math.max(16, plugin.getConfig().getInt("scoreboard.nametag_max_length", 48));
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String resolveActiveTag(PlayerProfile profile) {
        if (profile == null || profile.getActiveTag() == null || profile.getActiveTag().isBlank()) {
            return "";
        }
        if (plugin instanceof com.darkniightz.main.JebaitedCore core) {
            if (com.darkniightz.core.system.TagCustomizationManager.CUSTOM_TAG_KEY.equalsIgnoreCase(profile.getActiveTag())
                    && core.getTagCustomizationManager() != null) {
                String custom = core.getTagCustomizationManager().getCustomTag(profile.getUuid());
                return custom == null ? "" : ChatColor.stripColor(custom);
            }
            if (core.getCosmeticsManager() != null) {
                var cosmetic = core.getCosmeticsManager().get(profile.getActiveTag());
                if (cosmetic != null && cosmetic.name != null) {
                    return ChatColor.stripColor(cosmetic.name);
                }
            }
        }
        return ChatColor.stripColor(profile.getActiveTag());
    }

    private String resolveEquipped(PlayerProfile profile) {
        if (profile == null) {
            return "none";
        }
        if (profile.getActiveTag() != null && !profile.getActiveTag().isBlank()) {
            return "tag";
        }
        if (profile.getEquippedGadget() != null && !profile.getEquippedGadget().isBlank()) {
            return "gadget";
        }
        if (profile.getEquippedTrail() != null && !profile.getEquippedTrail().isBlank()) {
            return "trail";
        }
        if (profile.getEquippedParticles() != null && !profile.getEquippedParticles().isBlank()) {
            return "particle";
        }
        return "none";
    }

    private String compactNumber(double value) {
        double safe = Math.max(0D, value);
        if (safe >= 1_000_000_000_000D) return String.format(Locale.ROOT, "%.1ft", safe / 1_000_000_000_000D);
        if (safe >= 1_000_000_000D) return String.format(Locale.ROOT, "%.1fb", safe / 1_000_000_000D);
        if (safe >= 1_000_000D) return String.format(Locale.ROOT, "%.1fm", safe / 1_000_000D);
        if (safe >= 1_000D) return String.format(Locale.ROOT, "%.1fk", safe / 1_000D);
        return String.format(Locale.ROOT, "%.0f", safe);
    }

    private String compactNumber(long value) {
        return compactNumber((double) Math.max(0L, value));
    }

    private ChatColor resolveColor(RankManager.RankStyle style) {
        if (style == null) {
            return ChatColor.WHITE;
        }
        String code = style.colorCode;
        if ((code == null || code.isBlank()) && style.rainbowColors != null && !style.rainbowColors.isEmpty()) {
            code = "§" + style.rainbowColors.get(0);
        }
        if (code != null && code.length() >= 2) {
            ChatColor parsed = ChatColor.getByChar(code.charAt(code.length() - 1));
            if (parsed != null) {
                return parsed;
            }
        }
        return ChatColor.WHITE;
    }

    private String prettyRank(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Pleb";
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "owner"      -> "Owner";
            case "developer"  -> "Dev";
            case "admin"      -> "Admin";
            case "srmod"      -> "Sr.Mod";
            case "moderator"  -> "Mod";
            case "helper"     -> "Helper";
            case "vip"         -> "VIP";
            case "builder"     -> "Builder";
            case "grandmaster" -> "GM";
            case "legend"      -> "Legend";
            case "diamond"     -> "Diamond";
            case "gold"        -> "Gold";
            // legacy aliases kept for old data mid-migration
            case "supporter1"  -> "Gold";
            case "supporter2"  -> "Diamond";
            case "supporter3"  -> "Legend";
            case "pleb"        -> "Pleb";
            default -> {
                String lower = raw.toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }

    private String animatedTitle() {
        List<String> frames = plugin.getConfig().getStringList("scoreboard.title_frames");
        if (frames != null && !frames.isEmpty()) {
            int idx = Math.floorMod(frame, frames.size());
            return ChatColor.translateAlternateColorCodes('&', frames.get(idx));
        }
        return defaultRollingTitle(frame);
    }

    private String defaultRollingTitle(int step) {
        String text = "Jebaited";
        ChatColor[] colors = new ChatColor[] {
            ChatColor.DARK_AQUA,
            ChatColor.AQUA,
                ChatColor.WHITE,
                ChatColor.BLUE,
            ChatColor.GRAY
        };
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            ChatColor c = colors[Math.floorMod(step + i, colors.length)];
            out.append(c).append(ChatColor.BOLD).append(text.charAt(i));
        }
        return out.toString();
    }

    private void refreshDiscordLinkedCountAsync() {
        if (!plugin.getConfig().getBoolean("scoreboard.tablist.enabled", true)) {
            return;
        }
        if (!(plugin instanceof JebaitedCore core) || core.getDiscordLinkService() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long minIntervalMs = Math.max(3000L, plugin.getConfig().getLong("scoreboard.tablist.interval_seconds", 10L) * 1000L);
        if (discordRefreshInFlight || (now - lastDiscordLinkedRefreshMs) < minIntervalMs) {
            return;
        }
        discordRefreshInFlight = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                discordLinkedCount = core.getDiscordLinkService().countActiveLinks();
                lastDiscordLinkedRefreshMs = System.currentTimeMillis();
            } finally {
                discordRefreshInFlight = false;
            }
        });
    }

    private void applyTablist(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("scoreboard.tablist.enabled", true)) {
            return;
        }
        String header = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("scoreboard.server_name", "&fJebaited Network"));
        if (player.hasPermission(PermissionConstants.TABLIST_HIDE)) {
            String hidden = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("scoreboard.tablist.hidden_footer", "&fJebaited Network"));
            player.setPlayerListHeaderFooter(header, hidden);
            return;
        }
        List<String> categories = plugin.getConfig().getStringList("scoreboard.tablist.categories");
        if (categories == null || categories.isEmpty()) {
            categories = List.of("help", "store", "discord", "network");
        }
        long interval = Math.max(3L, plugin.getConfig().getLong("scoreboard.tablist.interval_seconds", 10L));
        int index = (int) ((System.currentTimeMillis() / 1000L / interval) % categories.size());
        String category = categories.get(Math.max(0, Math.min(index, categories.size() - 1))).toLowerCase(Locale.ROOT);
        String footer = switch (category) {
            case "store" -> "§6Store§7: §f/donate";
            case "discord" -> {
                if (discordLinkedCount >= 0) {
                    yield "§9Discord Linked§7: §f" + discordLinkedCount;
                }
                yield "§9Discord§7: §f/link";
            }
            case "network" -> "§aOnline§7: §f" + Bukkit.getOnlinePlayers().size() + "§7/§f" + Bukkit.getMaxPlayers();
            default -> "§bHelp§7: §f/jebaited";
        };
        player.setPlayerListHeaderFooter(header, footer);
    }
}
