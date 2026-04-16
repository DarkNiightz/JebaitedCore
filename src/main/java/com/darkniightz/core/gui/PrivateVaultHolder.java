package com.darkniightz.core.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Simple holder to identify Private Vault inventories, track current page and total pages.
 * Also optionally carries a {@code targetUUID} for staff inspection (vault owner ≠ viewer).
 * Used by PrivateVaultManager and inventory click/close listeners.
 */
public class PrivateVaultHolder implements InventoryHolder {

    private final int page;
    private final int maxPages;
    /** UUID of the vault OWNER. Null means the opener owns the vault. */
    private final UUID targetUUID;
    /** If true, changes are discarded on close (read-only staff view). */
    private final boolean readOnly;

    /** Standard self-viewing constructor. */
    public PrivateVaultHolder(int page, int maxPages) {
        this(page, maxPages, null, false);
    }

    /** Staff-inspection constructor. */
    public PrivateVaultHolder(int page, int maxPages, @Nullable UUID targetUUID, boolean readOnly) {
        this.page = Math.max(0, page);
        this.maxPages = Math.max(1, maxPages);
        this.targetUUID = targetUUID;
        this.readOnly = readOnly;
    }

    public int getPage() { return page; }
    public int getMaxPages() { return maxPages; }

    /** Returns the vault owner UUID, or null if the opener owns the vault. */
    @Nullable
    public UUID getTargetUUID() { return targetUUID; }

    public boolean isReadOnly() { return readOnly; }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("PrivateVaultHolder does not provide a default inventory");
    }
}

