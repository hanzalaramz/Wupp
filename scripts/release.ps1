param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$ChangelogText = "",
    [switch]$Push
)

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $PSScriptRoot
Push-Location $RootDir

try {
    $CleanVer = $Version.Trim().TrimStart("v", "V")
    if ($CleanVer -notmatch '^(\d+)\.(\d+)\.(\d+)(-.+)?$') {
        throw "Version must follow SemVer like 0.2.1 or 0.3.0-beta.1 (got '$Version')"
    }

    $Major = [int]$Matches[1]
    $Minor = [int]$Matches[2]
    $Patch = [int]$Matches[3]
    $Suffix = $Matches[4]

    $BaseCode = (($Major * 10000) + ($Minor * 100) + $Patch) * 100
    if ($Suffix) {
        if ($Suffix -match '(\d+)(?!.*\d)') {
            $PreNum = [int]$Matches[1]
        } else {
            $PreNum = 1
        }
        $PreNum = [Math]::Min(98, [Math]::Max(1, $PreNum))
    } else {
        $PreNum = 99
    }

    $PropsVersionCode = $BaseCode + $PreNum
    $UniversalCode = $PropsVersionCode * 10
    $Arm64Code = $UniversalCode + 2
    $Tag = "v$CleanVer"

    $Utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    $GradlePropsPath = Join-Path $RootDir "gradle.properties"
    $PropsLines = Get-Content $GradlePropsPath
    $UpdatedProps = ($PropsLines | ForEach-Object {
        if ($_ -match '^WUPP_VERSION_NAME=') { "WUPP_VERSION_NAME=$CleanVer" }
        elseif ($_ -match '^WUPP_VERSION_CODE=') { "WUPP_VERSION_CODE=$PropsVersionCode" }
        else { $_ }
    }) -join "`n"
    [System.IO.File]::WriteAllText($GradlePropsPath, $UpdatedProps + "`n", $Utf8NoBom)

    $ChangelogsDir = Join-Path $RootDir "fastlane\metadata\android\en-US\changelogs"
    New-Item -ItemType Directory -Force -Path $ChangelogsDir | Out-Null

    if ([string]::IsNullOrWhiteSpace($ChangelogText)) {
        $ChangelogText = "- Improvements and bug fixes in Wupp $CleanVer"
    }
    if ($ChangelogText.Length -gt 500) {
        Write-Warning "Fastlane changelog is $($ChangelogText.Length) chars (F-Droid truncates >500 chars)."
    }

    [System.IO.File]::WriteAllText((Join-Path $ChangelogsDir "$UniversalCode.txt"), $ChangelogText + "`n", $Utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $ChangelogsDir "$Arm64Code.txt"), $ChangelogText + "`n", $Utf8NoBom)

    $ChangelogMdPath = Join-Path $RootDir "CHANGELOG.md"
    $ChangelogContent = [System.IO.File]::ReadAllText($ChangelogMdPath, $Utf8NoBom)
    if ($ChangelogContent -notmatch "(?m)^## \[$([Regex]::Escape($CleanVer))\]") {
        $Today = (Get-Date).ToString("yyyy-MM-dd")
        $NewSection = "`n## [$CleanVer] - $Today`n`n$ChangelogText`n"
        $ChangelogContent = $ChangelogContent -replace '(?m)^## \[Unreleased\]\r?\n', "## [Unreleased]`n$NewSection"
        [System.IO.File]::WriteAllText($ChangelogMdPath, $ChangelogContent, $Utf8NoBom)
    }

    git add gradle.properties CHANGELOG.md fastlane/metadata/android/en-US/changelogs/
    git commit -m "chore(release): prepare $Tag (versionCode $PropsVersionCode)"
    git tag -a $Tag -m "Release $Tag"

    Write-Host "Prepared $Tag (WUPP_VERSION_CODE=$PropsVersionCode, APK versionCodes=$UniversalCode/$Arm64Code)"
    if ($Push) {
        git push origin HEAD
        git push origin $Tag
    } else {
        Write-Host "Run 'git push origin HEAD; git push origin $Tag' to trigger Release APK + F-Droid repo sync."
    }
} finally {
    Pop-Location
}
