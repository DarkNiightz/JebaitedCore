-- =============================================================================
-- V003 — One-time data backfills
-- These fix data that was missing or incorrect during early operation.
-- Running these more than once is harmless but wasteful — tracking them in
-- schema_migrations ensures they only ever run once, regardless of restarts.
-- =============================================================================

-- Backfill playtime_seconds from playtime_ms for rows where it was never set.
-- The WHERE guard keeps this a no-op once all rows are populated.
UPDATE player_stats
SET playtime_seconds = GREATEST(0, COALESCE(playtime_ms, 0) / 1000)
WHERE COALESCE(playtime_seconds, 0) = 0;

-- Backfill target_name on moderation_history records that predate the column.
UPDATE moderation_history mh
SET target_name = p.username
FROM players p
WHERE mh.target_name IS NULL
  AND mh.target_uuid = p.uuid;

-- Rename the legacy 'friend' rank to 'pleb'.
UPDATE players SET rank = 'pleb' WHERE rank = 'friend';
