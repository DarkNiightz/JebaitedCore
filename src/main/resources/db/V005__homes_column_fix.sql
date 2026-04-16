-- V005: Fix player_homes column renames that may have been silently skipped in V003.
-- V003 queries information_schema without table_schema = 'public', which can cause
-- the conditional rename to not fire on some PostgreSQL setups.
-- This migration adds table_schema guards and re-applies any outstanding renames.

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'player_homes' AND column_name = 'uuid'
  ) THEN
    ALTER TABLE player_homes RENAME COLUMN uuid TO player_uuid;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'player_homes' AND column_name = 'name'
  ) THEN
    ALTER TABLE player_homes RENAME COLUMN name TO home_name;
  END IF;
END $$;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'player_homes' AND column_name = 'world'
  ) THEN
    ALTER TABLE player_homes RENAME COLUMN world TO world_name;
  END IF;
END $$;

-- Ensure player_uuid exists as UUID (handles installs where table was created fresh by V001)
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'player_homes' AND column_name = 'player_uuid'
  ) THEN
    ALTER TABLE player_homes ADD COLUMN player_uuid UUID NOT NULL DEFAULT gen_random_uuid();
  END IF;
END $$;

-- Cast to UUID type if still varchar
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public' AND table_name = 'player_homes'
      AND column_name = 'player_uuid' AND data_type = 'character varying'
  ) THEN
    ALTER TABLE player_homes ALTER COLUMN player_uuid TYPE UUID USING player_uuid::UUID;
  END IF;
END $$;

-- Ensure home_name and world_name columns exist
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS home_name  VARCHAR(64)       NOT NULL DEFAULT '';
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS world_name VARCHAR(64)       NOT NULL DEFAULT '';
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS yaw        REAL              NOT NULL DEFAULT 0;
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS pitch      REAL              NOT NULL DEFAULT 0;
ALTER TABLE player_homes ADD COLUMN IF NOT EXISTS created_at BIGINT            NOT NULL DEFAULT 0;

-- Rebuild PK on (player_uuid, home_name) if missing
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_schema = 'public' AND table_name = 'player_homes'
      AND constraint_type = 'PRIMARY KEY'
  ) THEN
    ALTER TABLE player_homes ADD PRIMARY KEY (player_uuid, home_name);
  END IF;
END $$;
