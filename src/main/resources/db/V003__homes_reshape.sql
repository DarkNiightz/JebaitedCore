-- V003: Reshape player_homes to match HomesManager schema.
-- Idempotent: every step checks information_schema before executing.
-- Safe on both existing servers (old schema with uuid/name/world columns) and
-- fresh installs (table created by HomesManager.initDatabase() with new schema).
--
-- Old schema: id (serial PK), uuid (varchar), name (varchar), world (varchar), x, y, z, created_at (TIMESTAMPTZ)
-- New schema: player_uuid (UUID PK), home_name, world_name, x, y, z, yaw, pitch, created_at (BIGINT epoch ms)

-- 1. Rename uuid → player_uuid (old installs only)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'uuid'
  ) THEN
    ALTER TABLE player_homes RENAME COLUMN uuid TO player_uuid;
  END IF;
END $$;

-- 2. Rename name → home_name (old installs only)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'name'
  ) THEN
    ALTER TABLE player_homes RENAME COLUMN name TO home_name;
  END IF;
END $$;

-- 3. Rename world → world_name (old installs only)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'world'
  ) THEN
    ALTER TABLE player_homes RENAME COLUMN world TO world_name;
  END IF;
END $$;

-- 4. Cast player_uuid from VARCHAR to UUID type (old installs stored as VARCHAR(36))
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'player_uuid' AND data_type = 'character varying'
  ) THEN
    ALTER TABLE player_homes ALTER COLUMN player_uuid TYPE UUID USING player_uuid::UUID;
  END IF;
END $$;

-- 5. Ensure home_name is VARCHAR(64)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'home_name'
  ) THEN
    ALTER TABLE player_homes ALTER COLUMN home_name TYPE VARCHAR(64);
  END IF;
END $$;

-- 6. Add yaw and pitch (safe on both old and new installs)
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS yaw   REAL NOT NULL DEFAULT 0;
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS pitch REAL NOT NULL DEFAULT 0;

-- 7. Convert created_at from TIMESTAMPTZ → BIGINT epoch ms (old installs only)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'created_at'
      AND data_type = 'timestamp with time zone'
  ) THEN
    ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS created_at_ms BIGINT NOT NULL DEFAULT 0;
    UPDATE player_homes SET created_at_ms = EXTRACT(EPOCH FROM created_at)::BIGINT * 1000;
    ALTER TABLE player_homes DROP COLUMN created_at;
    ALTER TABLE player_homes RENAME COLUMN created_at_ms TO created_at;
  END IF;
END $$;

-- 8. Ensure created_at exists as BIGINT (fresh installs — no-op if already created by step 7 or HomesManager)
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS created_at BIGINT NOT NULL DEFAULT 0;

-- 9. Drop old id-based primary key and serial id column (old installs only)
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'player_homes' AND column_name = 'id'
  ) THEN
    ALTER TABLE player_homes DROP CONSTRAINT IF EXISTS player_homes_pkey;
    ALTER TABLE player_homes DROP CONSTRAINT IF EXISTS player_homes_uuid_name_key;
    ALTER TABLE player_homes DROP COLUMN IF EXISTS id;
  END IF;
END $$;

-- 10. Add composite primary key if none exists yet
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_name = 'player_homes' AND constraint_type = 'PRIMARY KEY'
  ) THEN
    ALTER TABLE player_homes ADD PRIMARY KEY (player_uuid, home_name);
  END IF;
END $$;

-- 11. Ensure player_uuid is NOT NULL
ALTER TABLE player_homes ALTER COLUMN player_uuid SET NOT NULL;
