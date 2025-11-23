package com.darkniightz.core;

import com.darkniightz.main.JebaitedCore;

public final class Messages {
    private Messages() {}

    public static String noPerm() {
        try {
            String s = JebaitedCore.getInstance().getConfig().getString(
                    "messages.no_permission",
                    "§cYou don't have permission to use this command.");
            return s != null ? s : "§cYou don't have permission to use this command.";
        } catch (Exception e) {
            return "§cYou don't have permission to use this command.";
        }
    }
}
