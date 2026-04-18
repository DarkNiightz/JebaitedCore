package com.darkniightz.core.permissions;

/**
 * Centralised permission constants shared between the plugin and the web panel.
 * <p>
 * Each constant has two faces:
 * <ul>
 *   <li><b>Bukkit node</b> – e.g. {@code jebaited.mod.kick} (checked via {@code player.hasPermission(…)})</li>
 *   <li><b>Web key</b> – e.g. {@code kick} (checked via the web panel's permissions.yml)</li>
 * </ul>
 * The {@link #webKey()} helper strips the common prefix so you can send the
 * short key to the panel API when needed.
 */
public final class PermissionConstants {

    private PermissionConstants() { }

    // ── Prefix ──────────────────────────────────────────
    public static final String PREFIX = "jebaited.";

    // ── Core ────────────────────────────────────────────
    public static final String CORE_USE        = "jebaited.core.use";
    public static final String MENU_USE        = "jebaited.menu.use";
    public static final String COSMETICS_OPEN  = "jebaited.cosmetics.open";
    public static final String RELOAD          = "jebaited.reload";

    // ── Moderation  (match web-panel permission keys) ───
    public static final String MOD_KICK        = "jebaited.mod.kick";
    public static final String MOD_WARN        = "jebaited.mod.warn";
    public static final String MOD_TEMPMUTE    = "jebaited.mod.tempmute";
    public static final String MOD_TEMPBAN     = "jebaited.mod.tempban";
    public static final String MOD_MUTE        = "jebaited.mod.mute";
    public static final String MOD_BAN         = "jebaited.mod.ban";
    public static final String MOD_UNBAN       = "jebaited.mod.unban";
    public static final String MOD_UNMUTE      = "jebaited.mod.unmute";
    public static final String MOD_FREEZE      = "jebaited.mod.freeze";
    public static final String MOD_VANISH      = "jebaited.mod.vanish";
    public static final String MOD_STAFFCHAT   = "jebaited.mod.staffchat";
    public static final String MOD_CLEARCHAT   = "jebaited.mod.clearchat";
    public static final String MOD_SLOWMODE    = "jebaited.mod.slowmode";
    public static final String MOD_HISTORY     = "jebaited.mod.history";

    // ── Ranks ───────────────────────────────────────────
    public static final String RANK_SET        = "jebaited.rank.set";
    public static final String RANK_VIEW       = "jebaited.rank.view";

    // ── Join priority ────────────────────────────────────
    /** Bypasses the priority-queue kick check; granted implicitly to all staff ranks. */
    public static final String JOIN_PRIORITY_BYPASS = "jebaited.join.priority_bypass";

    // ── Party ────────────────────────────────────────────
    public static final String CMD_PARTY      = "jebaited.party.use";
    public static final String CMD_PARTY_CHAT = "jebaited.party.chat";

    /** View own combat tag status (/combatlogs). */
    public static final String CMD_COMBATLOG    = "jebaited.combatlog.use";

    // ── mcMMO wrappers (optional soft-depend) ────────────
    public static final String CMD_MCSTATS        = "jebaited.mcmmo.mcstats";
    public static final String CMD_MCSTATS_OTHERS = "jebaited.mcmmo.mcstats.others";
    public static final String CMD_MCTOP          = "jebaited.mcmmo.mctop";
    public static final String CMD_MCINSPECT      = "jebaited.mcmmo.inspect";
    public static final String CMD_MCRANK         = "jebaited.mcmmo.mcrank";
    public static final String CMD_MCABILITY      = "jebaited.mcmmo.mcability";
    public static final String CMD_MCCOOLDOWN     = "jebaited.mcmmo.mccooldown";
    public static final String CMD_PTP            = "jebaited.mcmmo.ptp";

    // ── Achievements ─────────────────────────────────────
    public static final String CMD_ACHIEVEMENTS = "jebaited.achievements.use";

    /** Server balance shop GUI ({@code /shop}). */
    public static final String CMD_SHOP = "jebaited.shop.use";
    /** Stripe store / donate GUI ({@code /donate}). */
    public static final String CMD_DONATE = "jebaited.donate.use";
    /** Hardcore loot claim GUI ({@code /loot}). */
    public static final String CMD_LOOT = "jebaited.loot.use";
    /** Discord account linking ({@code /link}). */
    public static final String CMD_DISCORD_LINK = "jebaited.discord.link";

    /** Receives Discord → in-game messages on the faction bridge channel (optional). */
    public static final String DISCORD_BRIDGE_FACTION = "jebaited.discord.bridge.faction";
    /** Uses the compact tablist footer override. */
    public static final String TABLIST_HIDE = "jebaited.tablist.hide";

    /** Optional extra gate for /chatgame staff actions (primary gate is srmod+ in {@link com.darkniightz.core.commands.ChatGameCommand}). */
    public static final String CMD_CHATGAME = "jebaited.chatgame.use";

    // ── Donor Perks ──────────────────────────────────────
    public static final String CMD_BACK         = "jebaited.back.use";
    public static final String CMD_PV_INSPECT   = "jebaited.pv.inspect";
    public static final String DONOR_FEED       = "jebaited.donor.feed";
    public static final String DONOR_NEAR       = "jebaited.donor.near";
    public static final String DONOR_ENDERCHEST = "jebaited.donor.enderchest";
    public static final String DONOR_CRAFT      = "jebaited.donor.craft";
    public static final String DONOR_ANVIL      = "jebaited.donor.anvil";
    public static final String DONOR_REPAIR     = "jebaited.donor.repair";
    public static final String DONOR_DEATHTP    = "jebaited.donor.deathtp";
    public static final String DONOR_KIT        = "jebaited.donor.kit";
    public static final String KIT_GOLD         = "jebaited.kit.gold";
    public static final String KIT_DIAMOND      = "jebaited.kit.diamond";
    public static final String KIT_LEGEND       = "jebaited.kit.legend";
    public static final String KIT_GRANDMASTER  = "jebaited.kit.grandmaster";

    // ── Utility ─────────────────────────────────────────

    /**
     * Convert a Bukkit permission node (e.g. {@code jebaited.mod.kick})
     * to the short web-panel key (e.g. {@code kick}).
     */
    public static String webKey(String bukkitNode) {
        if (bukkitNode == null) return "";
        // Strip nested prefix like "jebaited.mod." or "jebaited.rank."
        int lastDot = bukkitNode.lastIndexOf('.');
        return lastDot >= 0 ? bukkitNode.substring(lastDot + 1) : bukkitNode;
    }
}
