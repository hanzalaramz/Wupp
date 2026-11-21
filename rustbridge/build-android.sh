#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ -z "${ANDROID_NDK_HOME:-}" ]]; then
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT/ndk" ]]; then
    ANDROID_NDK_HOME="$(ls -d "$ANDROID_SDK_ROOT"/ndk/* | sort -V | tail -n 1)"
  elif [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME/ndk" ]]; then
    ANDROID_NDK_HOME="$(ls -d "$ANDROID_HOME"/ndk/* | sort -V | tail -n 1)"
  else
    echo "ANDROID_NDK_HOME is not set and could not be auto-detected" >&2
    exit 1
  fi
fi

NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
if [[ ! -d "$NDK_BIN" ]]; then
  echo "NDK bin directory not found: $NDK_BIN" >&2
  exit 1
fi

export CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang"
export CXX_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang++"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
export RANLIB_aarch64_linux_android="$NDK_BIN/llvm-ranlib"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android26-clang"

(cd "$SCRIPT_DIR" && cargo build --release --target aarch64-linux-android "$@")

TARGET_DIR="${CARGO_TARGET_DIR:-$SCRIPT_DIR/target}"
JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$JNI_DIR"
cp "$TARGET_DIR/aarch64-linux-android/release/libwhatsbridge_rs.so" "$JNI_DIR/libwhatsbridge_rs.so"
ls -lh "$JNI_DIR/libwhatsbridge_rs.so"
