param(
    [string]$OutFile = "fdroid-repo-keystore.p12",
    [string]$Alias = "wupp-fdroid-repo",
    [switch]$UploadSecrets
)

$ErrorActionPreference = "Stop"

$Bytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($Bytes)
$Password = [Convert]::ToBase64String($Bytes).Replace("+", "A").Replace("/", "B").Replace("=", "")

if (Test-Path $OutFile) {
    throw "$OutFile already exists. Delete or back it up first so you don't overwrite an existing repo key."
}

keytool -genkeypair `
    -keystore $OutFile `
    -storetype PKCS12 `
    -alias $Alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -storepass $Password `
    -keypass $Password `
    -dname "CN=Wupp F-Droid Repo, O=Wupp, C=US" | Out-Null

$KeytoolOut = keytool -list -v -keystore $OutFile -storepass $Password -alias $Alias | Out-String
$Sha256Match = [regex]::Match($KeytoolOut, 'SHA256:\s*([A-F0-9:]+)')
$Fingerprint = if ($Sha256Match.Success) { $Sha256Match.Groups[1].Value.Replace(":", "") } else { "Check via keytool -list -v" }
$Base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($OutFile))

Write-Host ""
Write-Host "Generated $OutFile (BACK THIS FILE UP SAFELY, DO NOT COMMIT TO GIT)"
Write-Host "Repo SHA-256 Fingerprint: $Fingerprint"

if ($UploadSecrets) {
    $Base64 | gh secret set FDROID_REPO_KEYSTORE_BASE64
    $Password | gh secret set FDROID_REPO_KEYSTORE_PASS
    $Password | gh secret set FDROID_REPO_KEY_PASS
    $Alias | gh secret set FDROID_REPO_KEY_ALIAS
    Write-Host "Uploaded FDROID_REPO_KEYSTORE_* secrets to GitHub via gh."
} else {
    Write-Host "Re-run with -UploadSecrets to upload to GitHub via 'gh secret set', or set manually:"
    Write-Host "  FDROID_REPO_KEYSTORE_BASE64 = (base64 of $OutFile)"
    Write-Host "  FDROID_REPO_KEYSTORE_PASS   = $Password"
    Write-Host "  FDROID_REPO_KEY_PASS       = $Password"
    Write-Host "  FDROID_REPO_KEY_ALIAS      = $Alias"
}
