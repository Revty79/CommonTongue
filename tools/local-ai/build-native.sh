#!/usr/bin/env bash
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
ndk="$ANDROID_HOME/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin"
export CARGO_TARGET_DIR="$root/.local/local-ai/t5-build"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk/aarch64-linux-android26-clang"
export CC_aarch64_linux_android="$CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER"
export AR_aarch64_linux_android="$ndk/llvm-ar"
export RUSTFLAGS='-C link-arg=-Wl,-soname,libtrial_t5.so -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384'
cargo +1.91.0 fetch --locked --manifest-path "$root/tools/local-ai/t5/Cargo.toml" --target aarch64-linux-android
cargo +1.91.0 build --offline --locked --release --manifest-path "$root/tools/local-ai/t5/Cargo.toml" --target aarch64-linux-android
mkdir -p "$root/.local/local-ai/native/arm64-v8a"
cp "$CARGO_TARGET_DIR/aarch64-linux-android/release/libtrial_t5.so" "$root/.local/local-ai/native/arm64-v8a/"
cargo +1.91.0 test --offline --locked --release --manifest-path "$root/tools/local-ai/t5/Cargo.toml" --lib
