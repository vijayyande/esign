$ErrorActionPreference = "Stop"
. "$PSScriptRoot\tools.ps1"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven was not found. Install Maven 3.9 or place it in .tools/maven."
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set and the bundled JDK was not found under .tools/jdk."
}

Set-Location $RepoRoot
& mvn -N install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& mvn -pl common install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$javaHome = $env:JAVA_HOME
$mavenBin = Split-Path -Parent (Get-Command mvn).Source
foreach ($module in $EsignModules) {
    $name = $module.Name
    $command = @"
`$env:JAVA_HOME = '$javaHome'
`$env:Path = '$javaHome\bin;$mavenBin;' + `$env:Path
Set-Location '$RepoRoot'
mvn -f $name/pom.xml spring-boot:run
"@
    Start-Process powershell -WorkingDirectory $RepoRoot -ArgumentList @(
        "-NoExit",
        "-Command",
        $command
    )
    Write-Host "Started $name on port $($module.Port)."
}
Write-Host "eKYC 8083, CA 8084, ESP 8082, ASP 8081. Stop them with scripts/stop-all.ps1."
