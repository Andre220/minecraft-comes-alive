# Prints OPEN when an MCA dev client (or any Minecraft) seems to be running, else CLOSED. Checks three things, since a
# client started from VS Code hides its main class in an @argfile:
#  1. a java/javaw command line naming the Fabric/NeoForge dev launchers or a dev run folder;
#  2. a java/javaw window titled "Minecraft...";
#  3. a game log held open by the game (fabric/run or neoforge/run logs/latest.log cannot be opened exclusively).
# usage: powershell -File scripts/claude-tools/client_open.ps1
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$reasons = @()

$java = Get-CimInstance Win32_Process -Filter "Name like 'java%'"
foreach ($p in $java) {
    if ($p.CommandLine -match 'KnotClient|fabric\.dli|DevLaunch|devlaunch|runClient|[\\/]run[\\/]|ClientRunProgramArgs') {
        $reasons += "command line of $($p.ProcessId)"
    }
}
foreach ($p in Get-Process -Name java, javaw -ErrorAction SilentlyContinue) {
    if ($p.MainWindowTitle -match 'Minecraft') {
        $reasons += "window '$($p.MainWindowTitle)'"
    }
}
foreach ($log in @("$root\fabric\run\logs\latest.log", "$root\neoforge\run\logs\latest.log")) {
    if (Test-Path $log) {
        try {
            $stream = [System.IO.File]::Open($log, 'Open', 'ReadWrite', 'None')
            $stream.Close()
        } catch {
            $reasons += "log in use: $log"
        }
    }
}

if ($reasons.Count -gt 0) {
    "OPEN (" + ($reasons -join '; ') + ")"
} else {
    "CLOSED"
}
