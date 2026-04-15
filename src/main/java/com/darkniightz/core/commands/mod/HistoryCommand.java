package com.darkniightz.core.commands.mod;

import com.darkniightz.core.Messages;
import com.darkniightz.core.dev.DevModeManager;
import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import com.darkniightz.main.JebaitedCore;
import com.darkniightz.main.PlayerProfileDAO;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class HistoryCommand implements CommandExecutor, TabCompleter {
    private final ProfileStore profiles;
    private final RankManager ranks;
    private final DevModeManager devMode;

    public HistoryCommand(ProfileStore profiles, RankManager ranks, DevModeManager devMode) {
        this.profiles = profiles;
        this.ranks = ranks;
        this.devMode = devMode;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Permission: Helper+ (or DevMode bypass)
        if (sender instanceof Player p) {
            PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
            boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
            if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) {
                sender.sendMessage(Messages.noPerm());
                return true;
            }
        }

        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage(Messages.prefixed("§cConsole must specify a player: §f/"+label+" <player>")); return true; }
            target = p;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) { sender.sendMessage(Messages.prefixed("§cPlayer not found: §e"+args[0])); return true; }
        }

        // Ensure profile exists (creates DB row if new)
        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
        // Pull moderation history from the database
        PlayerProfileDAO dao = JebaitedCore.getInstance().getPlayerProfileDAO();
        java.util.List<java.util.Map<String,Object>> list = dao != null ? dao.getModerationHistory(target.getUniqueId(), 100) : java.util.List.of();
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        sender.sendMessage(Messages.prefixed("§6— Moderation History for §e"+name+" §6(§7"+list.size()+" entries§6) —"));
        if (list.isEmpty()) { sender.sendMessage(Messages.prefixed("§7No entries.")); return true; }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK);
        int shown = 0;
        for (int i = list.size()-1; i >= 0 && shown < 12; i--) {
            Map<String,Object> e = list.get(i);
            long ts = e.get("ts") instanceof Number n ? n.longValue() : System.currentTimeMillis();
            String type = String.valueOf(e.getOrDefault("type", "?"));
            String reason = e.get("reason") != null ? (" §8| §7"+e.get("reason")) : "";
            String actor = e.get("actor") != null ? (" §8by §e"+e.get("actor")) : "";
            sender.sendMessage(Messages.prefixed("§7[§f"+fmt.format(new Date(ts))+"§7] §b"+type+actor+reason));
            shown++;
        }
        if (list.size() > shown) sender.sendMessage(Messages.prefixed("§8(…"+(list.size()-shown)+" more hidden)"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return List.of();
        PlayerProfile actor = profiles.getOrCreate(p, ranks.getDefaultGroup());
        boolean bypass = devMode != null && devMode.isActive(p.getUniqueId());
        if (!bypass && !ranks.isAtLeast(actor.getPrimaryRank(), "helper")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
