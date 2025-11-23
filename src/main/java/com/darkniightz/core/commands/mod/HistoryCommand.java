package com.darkniightz.core.commands.mod;

import com.darkniightz.core.players.ProfileStore;
import com.darkniightz.core.players.PlayerProfile;
import com.darkniightz.core.ranks.RankManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class HistoryCommand implements CommandExecutor {
    private final ProfileStore profiles;
    private final RankManager ranks;

    public HistoryCommand(ProfileStore profiles, RankManager ranks) {
        this.profiles = profiles;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cConsole must specify a player: §e/"+label+" <player>"); return true; }
            target = p;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
            if (target.getUniqueId() == null) { sender.sendMessage("§cPlayer not found: §e"+args[0]); return true; }
        }

        PlayerProfile tp = profiles.getOrCreate(target, ranks.getDefaultGroup());
        var list = tp.getModerationLog();
        String name = target.getName() != null ? target.getName() : target.getUniqueId().toString().substring(0, 8);
        sender.sendMessage("§6— Moderation History for §e"+name+" §6(§7"+list.size()+" entries§6) —");
        if (list.isEmpty()) { sender.sendMessage("§7No entries."); return true; }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK);
        int shown = 0;
        for (int i = list.size()-1; i >= 0 && shown < 12; i--) {
            Map<String,Object> e = list.get(i);
            long ts = e.get("ts") instanceof Number n ? n.longValue() : System.currentTimeMillis();
            String type = String.valueOf(e.getOrDefault("type", "?"));
            String reason = e.get("reason") != null ? (" §8| §7"+e.get("reason")) : "";
            String actor = e.get("actor") != null ? (" §8by §e"+e.get("actor")) : "";
            sender.sendMessage("§7[§f"+fmt.format(new Date(ts))+"§7] §b"+type+actor+reason);
            shown++;
        }
        if (list.size() > shown) sender.sendMessage("§8(…"+(list.size()-shown)+" more hidden)");
        return true;
    }
}
