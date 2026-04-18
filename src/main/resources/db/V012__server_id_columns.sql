ALTER TABLE chat_logs
    ADD COLUMN IF NOT EXISTS server VARCHAR(16);

ALTER TABLE player_command_log
    ADD COLUMN IF NOT EXISTS server VARCHAR(16);

ALTER TABLE moderation_history
    ADD COLUMN IF NOT EXISTS server VARCHAR(16);

UPDATE chat_logs SET server = 'hub-01' WHERE server IS NULL;
UPDATE player_command_log SET server = 'hub-01' WHERE server IS NULL;
UPDATE moderation_history SET server = 'hub-01' WHERE server IS NULL;

CREATE INDEX IF NOT EXISTS idx_chat_logs_server_created
    ON chat_logs (server, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_player_command_log_server_timestamp
    ON player_command_log (server, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_moderation_history_server_timestamp
    ON moderation_history (server, timestamp DESC);
