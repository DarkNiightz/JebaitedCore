#!/usr/bin/env bash
# =============================================================================
# restart.sh — JebaitedMC Server Wrapper Script
# =============================================================================
# Usage: ./restart.sh
#
# This script runs the Minecraft server in an infinite restart loop.
# Before each server startup it:
#   1. Backs up worlds + essential config files with a timestamped archive
#   2. Prunes old backups by count (keep last N) or total size cap
#   3. Starts the server
#
# Paper's spigot.yml "restart-script" should point to this file so that
# Bukkit.spigot().restart() triggers a clean restart via this wrapper.
#
# Requirements: bash, tar, java on PATH
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# ── CONFIG (edit these) ──────────────────────────────────────────────────────
# ---------------------------------------------------------------------------

SERVER_JAR="paper.jar"               # Path to your server JAR
JAVA_OPTS="-Xms2G -Xmx4G -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions \
  -XX:+DisableExplicitGC -XX:+AlwaysPreTouch \
  -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 \
  -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 \
  -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem \
  -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs \
  -Daikars.new.flags=true"

BACKUP_DIR="./backups"               # Where to store backups
WORLDS=("world" "world_nether" "world_the_end" "world_smp" "world_events")  # Worlds to back up
BACKUP_CONFIGS=("plugins/JebaitedCore/config.yml" "server.properties" "spigot.yml" "paper.yml")
BACKUP_KEEP_COUNT=10                 # Keep this many most-recent backups (0 = unlimited)
BACKUP_MAX_SIZE_GB=5                 # Delete oldest backups if total exceeds this (0 = unlimited)
RESTART_DELAY=5                      # Seconds to wait between restart cycles
CRASH_RESTART=true                   # Restart even after crash (non-zero exit code)?
CRASH_DELAY=10                       # Extra delay before restarting after a crash

LOG_FILE="./logs/wrapper.log"
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
log() {
    local ts
    ts=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$ts] $*" | tee -a "$LOG_FILE"
}

# ---------------------------------------------------------------------------
# Backup function
# ---------------------------------------------------------------------------
do_backup() {
    local ts
    ts=$(date '+%Y%m%d_%H%M%S')
    local archive="$BACKUP_DIR/backup_${ts}.tar.gz"

    mkdir -p "$BACKUP_DIR"
    log "Creating backup: $archive"

    # Build a list of things to archive (only include paths that exist)
    local items=()
    for w in "${WORLDS[@]}"; do
        [ -d "$w" ] && items+=("$w")
    done
    for c in "${BACKUP_CONFIGS[@]}"; do
        [ -f "$c" ] && items+=("$c")
    done

    if [ ${#items[@]} -eq 0 ]; then
        log "WARNING: Nothing to back up — worlds/configs not found. Skipping."
        return
    fi

    tar -czf "$archive" "${items[@]}" 2>>"$LOG_FILE" || {
        log "WARNING: Backup failed for $archive — continuing anyway."
        rm -f "$archive"
        return
    }

    local size_mb
    size_mb=$(du -sm "$archive" 2>/dev/null | cut -f1 || echo "?")
    log "Backup complete: $archive (${size_mb} MB)"

    prune_backups
}

# ---------------------------------------------------------------------------
# Prune old backups
# ---------------------------------------------------------------------------
prune_backups() {
    # Count-based pruning
    if [ "${BACKUP_KEEP_COUNT}" -gt 0 ] 2>/dev/null; then
        local existing
        mapfile -t existing < <(ls -t "$BACKUP_DIR"/backup_*.tar.gz 2>/dev/null)
        local count=${#existing[@]}
        if [ "$count" -gt "$BACKUP_KEEP_COUNT" ]; then
            local excess=$(( count - BACKUP_KEEP_COUNT ))
            log "Pruning $excess old backup(s) (keep last $BACKUP_KEEP_COUNT)..."
            for (( i=BACKUP_KEEP_COUNT; i<count; i++ )); do
                log "  Removing: ${existing[$i]}"
                rm -f "${existing[$i]}"
            done
        fi
    fi

    # Size-based pruning
    if [ "${BACKUP_MAX_SIZE_GB:-0}" -gt 0 ] 2>/dev/null; then
        local max_kb=$(( BACKUP_MAX_SIZE_GB * 1024 * 1024 ))
        while true; do
            local total_kb
            total_kb=$(du -sk "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo 0)
            if [ "$total_kb" -le "$max_kb" ]; then break; fi
            # Remove the oldest backup
            local oldest
            oldest=$(ls -t "$BACKUP_DIR"/backup_*.tar.gz 2>/dev/null | tail -1)
            if [ -z "$oldest" ]; then break; fi
            log "  Size cap exceeded (${total_kb}KB > ${max_kb}KB), removing oldest: $oldest"
            rm -f "$oldest"
        done
    fi
}

# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------
mkdir -p "$(dirname "$LOG_FILE")"
log "======================================================"
log " JebaitedMC Wrapper started (PID $$)"
log "======================================================"

RESTART_COUNT=0

while true; do
    RESTART_COUNT=$(( RESTART_COUNT + 1 ))
    log "--- Startup #$RESTART_COUNT ---"

    # Backup before every start
    do_backup

    log "Launching: java $JAVA_OPTS -jar $SERVER_JAR --nogui"
    set +e
    java $JAVA_OPTS -jar "$SERVER_JAR" --nogui
    EXIT_CODE=$?
    set -e

    log "Server exited with code $EXIT_CODE"

    if [ $EXIT_CODE -eq 0 ]; then
        log "Clean shutdown / restart requested. Restarting in ${RESTART_DELAY}s..."
        sleep "$RESTART_DELAY"
    else
        if [ "$CRASH_RESTART" = true ]; then
            log "Server crashed (exit $EXIT_CODE). Restarting in ${CRASH_DELAY}s..."
            sleep "$CRASH_DELAY"
        else
            log "Server crashed (exit $EXIT_CODE) and CRASH_RESTART=false. Stopping wrapper."
            break
        fi
    fi
done

log "Wrapper exiting."
