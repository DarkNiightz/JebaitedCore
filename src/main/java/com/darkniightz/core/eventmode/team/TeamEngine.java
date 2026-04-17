package com.darkniightz.core.eventmode.team;

import com.darkniightz.core.eventmode.EventSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Even split of participants into red/blue for CTF.
 * Party-cohesion balancing is deferred (see ROADMAP §21).
 */
public final class TeamEngine {

    private TeamEngine() {}

    /** Clears and repopulates {@link EventSession#ctfTeamRed} / {@link EventSession#ctfTeamBlue}. */
    public static void assignCtfTeams(EventSession session) {
        session.ctfTeamRed.clear();
        session.ctfTeamBlue.clear();
        List<UUID> ids = new ArrayList<>(session.active);
        Collections.shuffle(ids);
        for (int i = 0; i < ids.size(); i++) {
            if (i % 2 == 0) {
                session.ctfTeamRed.add(ids.get(i));
            } else {
                session.ctfTeamBlue.add(ids.get(i));
            }
        }
    }

    public static Team teamOf(EventSession session, UUID playerId) {
        if (session.ctfTeamRed.contains(playerId)) return Team.RED;
        if (session.ctfTeamBlue.contains(playerId)) return Team.BLUE;
        return null;
    }
}
