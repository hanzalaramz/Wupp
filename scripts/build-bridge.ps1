param(
    [string]$Targets = "android/arm64,android/arm,android/amd64"
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
$GoBin = Join-Path (go env GOPATH) "bin"
if ($env:Path -notlike "*$GoBin*") {
    $env:Path = "$env:Path;$GoBin"
}

if (-not (Get-Command gomobile -ErrorAction SilentlyContinue)) {
    go install golang.org/x/mobile/cmd/gomobile@latest
    go install golang.org/x/mobile/cmd/gobind@latest
}

gomobile init
$LibsDir = Join-Path $RootDir "app\libs"
New-Item -ItemType Directory -Force -Path $LibsDir | Out-Null

Push-Location (Join-Path $RootDir "whatsbridge")
try {
    gomobile bind -javapkg=com.wa.bridge -ldflags="-s -w" -target="$Targets" -androidapi 26 -o (Join-Path $LibsDir "whatsbridge.aar") .
} finally {
    Pop-Location
}
Write-Host "Built $(Join-Path $LibsDir 'whatsbridge.aar')"
