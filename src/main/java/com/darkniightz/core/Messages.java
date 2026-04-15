package com.darkniightz.core;

import com.darkniightz.main.JebaitedCore;

public final class Messages {
    private Messages() {}

    public static String prefix() {
        try {
            String s = JebaitedCore.getInstance().getConfig().getString(
                    "messages.prefix",
                    "§8[§bJebaited§8] §7");
            return s != null ? s : "§8[§bJebaited§8] §7";
        } catch (Exception e) {
            return "§8[§bJebaited§8] §7";
        }
    }

    public static String prefixed(String message) {
        return prefix() + (message == null ? "" : message);
    }

    public static String noPerm() {
        try {
            String s = JebaitedCore.getInstance().getConfig().getString(
                    "messages.no_permission",
                    "§cYou don't have permission to use this command.");
            return prefixed(s != null ? s : "§cYou don't have permission to use this command.");
        } catch (Exception e) {
            return prefixed("§cYou don't have permission to use this command.");
        }
    }

    public static String hubOnly() {
        try {
            String s = JebaitedCore.getInstance().getConfig().getString(
                    "messages.hub_only_command",
                    "§cThat command is only available in the Hub world.");
            return prefixed(s != null ? s : "§cThat command is only available in the Hub world.");
        } catch (Exception e) {
            return prefixed("§cThat command is only available in the Hub world.");
        }
    }
}
