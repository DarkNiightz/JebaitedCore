package com.darkniightz.core.eventmode;

import org.bukkit.GameMode;
import org.bukkit.Location;

/** Staff/non-participant spectator: restore point + prior gamemode. */
public record SpectatorVisitState(Location returnLocation, GameMode previousMode) {}
