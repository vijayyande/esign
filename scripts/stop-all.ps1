$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tools.ps1"

function Get-ProcessRow([int] $processId) {
    Get-CimInstance Win32_Process -Filter "ProcessId=$processId" -ErrorAction SilentlyContinue
}

$roots = @{}
foreach ($module in $EsignModules) {
    $listeners = Get-NetTCPConnection -LocalPort $module.Port -State Listen -ErrorAction SilentlyContinue
    foreach ($listener in $listeners) {
        $current = Get-ProcessRow $listener.OwningProcess
        if (-not $current -or $current.CommandLine -notmatch "com\.esign\.") {
            continue
        }
        $root = $current
        while ($true) {
            $parent = Get-ProcessRow $root.ParentProcessId
            if (-not $parent) { break }
            $launch = $parent.Name -eq "java.exe" -and
                $parent.CommandLine -match "esign|spring-boot|classworlds|mvn"
            if (-not $launch) { break }
            $root = $parent
        }
        $roots[$root.ProcessId] = $module.Name
    }
}

if ($roots.Count -eq 0) {
    Write-Host "No eSign services are listening on ports 8081-8084."
    exit 0
}

foreach ($processId in $roots.Keys) {
    & taskkill /F /T /PID $processId | Out-Host
    Write-Host "Stopped $($roots[$processId]) (pid $processId)."
}
