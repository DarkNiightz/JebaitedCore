# =============================================================================
# restart.ps1 — JebaitedMC Server Wrapper Script (Windows)
# =============================================================================
# Usage: powershell -ExecutionPolicy Bypass -File restart.ps1
#
# Mirrors restart.sh for local development on Windows.
# =============================================================================

# ---------------------------------------------------------------------------
# CONFIG
# ---------------------------------------------------------------------------
$ServerJar       = "paper.jar"
$JavaOpts        = "-Xms2G -Xmx4G -XX:+UseG1GC"
$BackupDir       = ".\backups"
$Worlds          = @("world", "world_nether", "world_the_end", "world_smp", "world_events")
$BackupConfigs   = @("plugins\JebaitedCore\config.yml", "server.properties", "spigot.yml")
$BackupKeepCount = 10          # 0 = unlimited
$BackupMaxSizeGB = 5           # 0 = unlimited
$RestartDelay    = 5           # seconds
$CrashRestart    = $true
$CrashDelay      = 10
$LogFile         = ".\logs\wrapper.log"
# ---------------------------------------------------------------------------

function Write-Log {
    param([string]$Message)
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    $line = "[$ts] $Message"
    Write-Host $line
    $null = New-Item -ItemType File -Force -Path $LogFile
    Add-Content -Path $LogFile -Value $line
}

function Invoke-Backup {
    $ts = (Get-Date).ToString("yyyyMMdd_HHmmss")
    $archive = Join-Path $BackupDir "backup_$ts.zip"

    $null = New-Item -ItemType Directory -Force -Path $BackupDir

    $items = @()
    foreach ($w in $Worlds) {
        if (Test-Path $w) { $items += $w }
    }
    foreach ($c in $BackupConfigs) {
        if (Test-Path $c) { $items += $c }
    }

    if ($items.Count -eq 0) {
        Write-Log "WARNING: Nothing to back up. Skipping."
        return
    }

    Write-Log "Creating backup: $archive"
    try {
        Compress-Archive -Path $items -DestinationPath $archive -Force
        $sizeMB = [math]::Round((Get-Item $archive).Length / 1MB, 1)
        Write-Log "Backup complete: $archive ($sizeMB MB)"
    } catch {
        Write-Log "WARNING: Backup failed: $_"
        Remove-Item -Path $archive -ErrorAction SilentlyContinue
        return
    }

    Invoke-Prune
}

function Invoke-Prune {
    # Count-based
    if ($BackupKeepCount -gt 0) {
        $existing = Get-ChildItem -Path $BackupDir -Filter "backup_*.zip" |
                    Sort-Object LastWriteTime -Descending
        if ($existing.Count -gt $BackupKeepCount) {
            $toRemove = $existing | Select-Object -Skip $BackupKeepCount
            foreach ($f in $toRemove) {
                Write-Log "  Removing old backup: $($f.Name)"
                Remove-Item $f.FullName -Force
            }
        }
    }

    # Size-based
    if ($BackupMaxSizeGB -gt 0) {
        $maxBytes = $BackupMaxSizeGB * 1GB
        while ($true) {
            $files = Get-ChildItem -Path $BackupDir -Filter "backup_*.zip" |
                     Sort-Object LastWriteTime -Descending
            $totalBytes = ($files | Measure-Object -Property Length -Sum).Sum
            if ($null -eq $totalBytes -or $totalBytes -le $maxBytes) { break }
            $oldest = $files | Select-Object -Last 1
            if ($null -eq $oldest) { break }
            Write-Log "  Size cap exceeded, removing: $($oldest.Name)"
            Remove-Item $oldest.FullName -Force
        }
    }
}

# ---------------------------------------------------------------------------
# Main loop
# ---------------------------------------------------------------------------
$null = New-Item -ItemType Directory -Force -Path (Split-Path $LogFile)
Write-Log "======================================================"
Write-Log " JebaitedMC Wrapper started (PID $PID)"
Write-Log "======================================================"

$restartCount = 0

while ($true) {
    $restartCount++
    Write-Log "--- Startup #$restartCount ---"

    Invoke-Backup

    $cmdLine = "java $JavaOpts -jar `"$ServerJar`" --nogui"
    Write-Log "Launching: $cmdLine"

    $proc = Start-Process -FilePath "java" `
        -ArgumentList ($JavaOpts + " -jar `"$ServerJar`" --nogui") `
        -NoNewWindow -PassThru -Wait
    $exitCode = $proc.ExitCode

    Write-Log "Server exited with code $exitCode"

    if ($exitCode -eq 0) {
        Write-Log "Clean restart. Restarting in ${RestartDelay}s..."
        Start-Sleep -Seconds $RestartDelay
    } else {
        if ($CrashRestart) {
            Write-Log "Server crashed (exit $exitCode). Restarting in ${CrashDelay}s..."
            Start-Sleep -Seconds $CrashDelay
        } else {
            Write-Log "Server crashed and CrashRestart=false. Stopping."
            break
        }
    }
}

Write-Log "Wrapper exiting."
