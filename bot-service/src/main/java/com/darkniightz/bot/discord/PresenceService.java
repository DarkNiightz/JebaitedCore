package com.darkniightz.bot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;

public final class PresenceService {
    private final JDA jda;

    public PresenceService(JDA jda) {
        this.jda = jda;
    }

    public void setStatusLine(String statusText) {
        jda.getPresence().setActivity(Activity.customStatus(statusText));
    }
}
