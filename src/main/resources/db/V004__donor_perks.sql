-- V004: Donor Perk persistence
-- Adds kit_cooldowns JSONB column to the players table.
-- Idempotent: ADD COLUMN IF NOT EXISTS is safe on both fresh installs and existing DBs.

ALTER TABLE players ADD COLUMN IF NOT EXISTS kit_cooldowns JSONB NOT NULL DEFAULT '{}'::jsonb;
