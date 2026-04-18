# Build JebaitedCore and copy JebaitedCore.jar into your Paper server's plugins folder.
#
# Usage (PowerShell, from repo root that contains this scripts/ folder):
#   .\scripts\deploy-jebaited.ps1 -PluginsDir "C:\Paper\plugins"
#
# Or set once per session:
#   $env:JEBAITED_PLUGINS_DIR = "C:\Paper\plugins"
#   .\mvnw.cmd -f ..\mvnw.cmd  # wrong — see README in repo
#
# Then run Maven from the module folder (where pom.xml lives):
#   cd ...\JebaitedCore\JebaitedCore
#   $env:JEBAITED_PLUGINS_DIR = "C:\Paper\plugins"
#   ..\mvnw.cmd clean package
# Maven will echo "[JebaitedCore] Copying ..." when copy runs.

param(
    [Parameter(Mandatory = $false)]
    [string] $PluginsDir = $env:JEBAITED_PLUGINS_DIR
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $here

$mvnw = Join-Path (Split-Path $here -Parent) "mvnw.cmd"
if (-not (Test-Path $mvnw)) {
    $mvnw = Join-Path $here "mvnw.cmd"
}

if (-not (Test-Path $mvnw)) {
    Write-Error "mvnw.cmd not found. Run this from the JebaitedCore module folder; expected parent: $(Split-Path $here -Parent)"
    exit 1
}

if (-not $PluginsDir -or $PluginsDir.Trim() -eq "") {
    Write-Error "Pass -PluginsDir 'C:\YourPaper\plugins' or set environment variable JEBAITED_PLUGINS_DIR."
    exit 1
}

$pluginsResolved = (Resolve-Path -LiteralPath $PluginsDir -ErrorAction SilentlyContinue)
if (-not $pluginsResolved) {
    New-Item -ItemType Directory -Path $PluginsDir -Force | Out-Null
}

$env:JEBAITED_PLUGINS_DIR = $PluginsDir
Write-Host "Building and copying to: $PluginsDir"
& $mvnw -q -DskipTests clean package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Host "Done. Restart your Paper server (or /jreload is not enough for jar changes — stop, replace, start)."
