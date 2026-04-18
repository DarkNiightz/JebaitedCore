package com.darkniightz.core.eventmode.team;

import com.darkniightz.core.eventmode.EventSession;
import com.darkniightz.core.party.Party;
import com.darkniightz.core.party.PartyManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CTF team assignment: keeps full parties on the same side when possible, then balances by headcount.
 */
public final class TeamEngine {

    private TeamEngine() {}

    /** Clears and repopulates {@link EventSession#ctfTeamRed} / {@link EventSession#ctfTeamBlue}. */
    public static void assignCtfTeams(EventSession session, PartyManager parties) {
        session.ctfTeamRed.clear();
        session.ctfTeamBlue.clear();
        List<UUID> activeList = new ArrayList<>(session.active);
        List<List<UUID>> groups = new ArrayList<>();
        Set<UUID> placed = new HashSet<>();
        for (UUID id : activeList) {
            if (placed.contains(id)) {
                continue;
            }
            if (parties != null) {
                Party party = parties.getParty(id);
                if (party != null) {
                    List<UUID> g = new ArrayList<>();
                    for (UUID m : party.getMembers()) {
                        if (session.active.contains(m)) {
                            g.add(m);
                            placed.add(m);
                        }
                    }
                    if (!g.isEmpty()) {
                        groups.add(g);
                    }
                    continue;
                }
            }
            groups.add(List.of(id));
            placed.add(id);
        }
        Collections.shuffle(groups);
        for (List<UUID> group : groups) {
            boolean toRed = session.ctfTeamRed.size() <= session.ctfTeamBlue.size();
            for (UUID pid : group) {
                if (toRed) {
                    session.ctfTeamRed.add(pid);
                } else {
                    session.ctfTeamBlue.add(pid);
                }
            }
        }
    }

    public static Team teamOf(EventSession session, UUID playerId) {
        if (session.ctfTeamRed.contains(playerId)) return Team.RED;
        if (session.ctfTeamBlue.contains(playerId)) return Team.BLUE;
        return null;
    }
}
