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

export AR_aarch64_linux_android="$NDK_BIN/llvm-ar"
export RANLIB_aarch64_linux_android="$NDK_BIN/llvm-ranlib"
export AR_armv7_linux_androideabi="$NDK_BIN/llvm-ar"
export RANLIB_armv7_linux_androideabi="$NDK_BIN/llvm-ranlib"
export AR_x86_64_linux_android="$NDK_BIN/llvm-ar"
export RANLIB_x86_64_linux_android="$NDK_BIN/llvm-ranlib"

export CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang"
export CXX_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang++"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android26-clang"

export CC_armv7_linux_androideabi="$NDK_BIN/armv7a-linux-androideabi26-clang"
export CXX_armv7_linux_androideabi="$NDK_BIN/armv7a-linux-androideabi26-clang++"
export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$NDK_BIN/armv7a-linux-androideabi26-clang"

export CC_x86_64_linux_android="$NDK_BIN/x86_64-linux-android26-clang"
export CXX_x86_64_linux_android="$NDK_BIN/x86_64-linux-android26-clang++"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$NDK_BIN/x86_64-linux-android26-clang"

ABIS="${WUPP_RUST_ABIS:-arm64-v8a}"
TARGET_DIR="${CARGO_TARGET_DIR:-$SCRIPT_DIR/target}"

for ABI in $ABIS; do
  case "$ABI" in
    arm64-v8a)
      TARGET="aarch64-linux-android"
      ;;
    armeabi-v7a)
      TARGET="armv7-linux-androideabi"
      ;;
    x86_64)
      TARGET="x86_64-linux-android"
      ;;
    *)
      echo "Unsupported ABI in WUPP_RUST_ABIS: $ABI" >&2
      exit 1
      ;;
  esac

  (cd "$SCRIPT_DIR" && cargo build --release --target "$TARGET" "$@")
  JNI_DIR="$REPO_ROOT/app/src/main/jniLibs/$ABI"
  mkdir -p "$JNI_DIR"
  cp "$TARGET_DIR/$TARGET/release/libwhatsbridge_rs.so" "$JNI_DIR/libwhatsbridge_rs.so"
  ls -lh "$JNI_DIR/libwhatsbridge_rs.so"
done
