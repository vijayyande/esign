# Shared JDK and Maven resolution for the scripts in this folder.
$script:RepoRoot = Split-Path -Parent $PSScriptRoot

$bundledJdk = Join-Path $RepoRoot ".tools\jdk\jdk-17.0.20.1+1"
$bundledMvn = Join-Path $RepoRoot ".tools\maven\apache-maven-3.9.9\bin"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    if (Test-Path (Join-Path $bundledJdk "bin\java.exe")) {
        $env:JAVA_HOME = $bundledJdk
    }
}
if ($env:JAVA_HOME) {
    $env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    if (Test-Path (Join-Path $bundledMvn "mvn.cmd")) {
        $env:Path = $bundledMvn + ";" + $env:Path
    }
}

$script:EsignModules = @(
    @{ Name = "ekyc"; Port = 8083 },
    @{ Name = "ca"; Port = 8084 },
    @{ Name = "esp"; Port = 8082 },
    @{ Name = "asp"; Port = 8081 }
)
