$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Split-Path -Parent $ScriptDir

$CargoBin = Join-Path $env:USERPROFILE ".cargo\bin"
$RustupToolchain = Join-Path $env:USERPROFILE ".rustup\toolchains\1.100.0-x86_64-pc-windows-gnu"
$SelfContained = Join-Path $RustupToolchain "lib\rustlib\x86_64-pc-windows-gnu\bin\self-contained"

if (-not $env:ANDROID_NDK_HOME) {
    $SdkRoots = @(
        "C:\Android\Sdk\ndk",
        (Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk")
    ) | Where-Object { Test-Path $_ }
    $FoundNdk = $SdkRoots | ForEach-Object { Get-ChildItem $_ -Directory | Sort-Object Name -Descending } | Select-Object -First 1
    if (-not $FoundNdk) {
        throw "Could not find Android NDK. Set ANDROID_NDK_HOME."
    }
    $env:ANDROID_NDK_HOME = $FoundNdk.FullName
}

$NdkBin = Join-Path $env:ANDROID_NDK_HOME "toolchains\llvm\prebuilt\windows-x86_64\bin"
if (-not (Test-Path $NdkBin)) {
    throw "NDK toolchain bin not found at $NdkBin"
}

if (-not $env:CARGO_TARGET_DIR) {
    $CDrive = Get-PSDrive C -ErrorAction SilentlyContinue
    if ($CDrive -and ($CDrive.Free -lt 5GB) -and (Test-Path "D:\")) {
        $env:CARGO_TARGET_DIR = "D:\wupp-rust-target"
    } else {
        $env:CARGO_TARGET_DIR = Join-Path $ScriptDir "target"
    }
}
$TargetDir = $env:CARGO_TARGET_DIR
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null

# Ensure GNU binutils shims exist for Windows proc-macro/build-script compilation
$ToolsDir = Join-Path $TargetDir "host-tools"
New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
$AsExe = Join-Path $ToolsDir "as.exe"
$DllToolExe = Join-Path $ToolsDir "dlltool.exe"
if ((-not (Test-Path $AsExe)) -or (-not (Test-Path $DllToolExe))) {
    $Rustc = Join-Path $CargoBin "rustc.exe"
    $Clang = Join-Path $NdkBin "clang.exe"
    $OrigDllTool = Join-Path $SelfContained "dlltool.exe"
    $AsSrc = Join-Path $ToolsDir "as_wrap.rs"
    $DtSrc = Join-Path $ToolsDir "dlltool_wrap.rs"
    @"
use std::process::{Command, exit};
fn main() {
    let args: Vec<String> = std::env::args().skip(1)
        .filter(|a| a != "--64" && a != "--no-leading-underscore" && a != "--32")
        .collect();
    let status = Command::new(r"$Clang")
        .arg("--target=x86_64-pc-windows-gnu")
        .arg("-c")
        .arg("-x")
        .arg("assembler")
        .args(&args)
        .status()
        .expect("clang");
    exit(status.code().unwrap_or(1));
}
"@ | Set-Content -Path $AsSrc -Encoding ASCII
    @"
use std::process::{Command, exit};
fn main() {
    let args: Vec<String> = std::env::args().skip(1).collect();
    let status = Command::new(r"$OrigDllTool")
        .arg("-S")
        .arg(r"$AsExe")
        .args(&args)
        .status()
        .expect("dlltool");
    exit(status.code().unwrap_or(1));
}
"@ | Set-Content -Path $DtSrc -Encoding ASCII
    & $Rustc $AsSrc -O -o $AsExe
    & $Rustc $DtSrc -O -o $DllToolExe
}

$env:PATH = "$ToolsDir;$CargoBin;$SelfContained;$env:PATH"
$env:AR_aarch64_linux_android = Join-Path $NdkBin "llvm-ar.exe"
$env:RANLIB_aarch64_linux_android = Join-Path $NdkBin "llvm-ranlib.exe"
$env:AR_armv7_linux_androideabi = Join-Path $NdkBin "llvm-ar.exe"
$env:RANLIB_armv7_linux_androideabi = Join-Path $NdkBin "llvm-ranlib.exe"
$env:AR_x86_64_linux_android = Join-Path $NdkBin "llvm-ar.exe"
$env:RANLIB_x86_64_linux_android = Join-Path $NdkBin "llvm-ranlib.exe"

$env:CC_aarch64_linux_android = Join-Path $NdkBin "aarch64-linux-android26-clang.cmd"
$env:CXX_aarch64_linux_android = Join-Path $NdkBin "aarch64-linux-android26-clang++.cmd"
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = Join-Path $NdkBin "aarch64-linux-android26-clang.cmd"

$env:CC_armv7_linux_androideabi = Join-Path $NdkBin "armv7a-linux-androideabi26-clang.cmd"
$env:CXX_armv7_linux_androideabi = Join-Path $NdkBin "armv7a-linux-androideabi26-clang++.cmd"
$env:CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER = Join-Path $NdkBin "armv7a-linux-androideabi26-clang.cmd"

$env:CC_x86_64_linux_android = Join-Path $NdkBin "x86_64-linux-android26-clang.cmd"
$env:CXX_x86_64_linux_android = Join-Path $NdkBin "x86_64-linux-android26-clang++.cmd"
$env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER = Join-Path $NdkBin "x86_64-linux-android26-clang.cmd"

$AbiToTarget = @{
    "arm64-v8a"   = "aarch64-linux-android"
    "armeabi-v7a" = "armv7-linux-androideabi"
    "x86_64"      = "x86_64-linux-android"
}
$RequestedAbis = if ($env:WUPP_RUST_ABIS) {
    $env:WUPP_RUST_ABIS -split "[\s,]+" | Where-Object { $_ }
} else {
    @("arm64-v8a")
}

Push-Location $ScriptDir
try {
    foreach ($Abi in $RequestedAbis) {
        if (-not $AbiToTarget.ContainsKey($Abi)) {
            throw "Unsupported ABI in WUPP_RUST_ABIS: $Abi"
        }
        $Target = $AbiToTarget[$Abi]
        & (Join-Path $CargoBin "cargo.exe") build --release --target $Target @args
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

        $BuiltSo = Join-Path $TargetDir "$Target\release\libwhatsbridge_rs.so"
        $JniDir = Join-Path $RepoRoot "app\src\main\jniLibs\$Abi"
        New-Item -ItemType Directory -Force -Path $JniDir | Out-Null
        Copy-Item -Force $BuiltSo (Join-Path $JniDir "libwhatsbridge_rs.so")
        Write-Host "Copied $BuiltSo -> $JniDir\libwhatsbridge_rs.so ($((Get-Item $BuiltSo).Length) bytes)"
    }
} finally {
    Pop-Location
}
