#!/usr/bin/env bash
#
# Builds the native cores and installs them into app/src/main/jniLibs/<abi>/:
#
#   libhev-socks5-tunnel.so <- hev's OWN JNI library (hev-jni.c), built with
#                    -DPKGNAME=studio/cluvex/aether/core so JNI_OnLoad registers
#                    the TProxy* natives on our TProxyService class. Runs
#                    IN-PROCESS (the VpnService TUN fd is per-process) on a
#                    native pthread hev creates itself (v2rayNG's exact mode).
#   libaether.so  <- the Aether engine, cross-compiled from Rust with cargo-ndk.
#
# Usage:  build-natives.sh [hev|aether|all]   (default: all)
#
# Requires: ANDROID_NDK_HOME, rustup android targets, cargo-ndk.
# Run scripts/fetch-natives.sh first.
set -euo pipefail

TARGET="${1:-all}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
NATIVE_DIR="${PROJECT_DIR}/.native"
HEV_DIR="${NATIVE_DIR}/hev-socks5-tunnel"
AETHER_SRC="${NATIVE_DIR}/aether"
JNI_DIR="${PROJECT_DIR}/app/src/main/jniLibs"

API="${ANDROID_API:-26}"
ABIS=("arm64-v8a" "armeabi-v7a")

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "${ANDROID_NDK_HOME}" ]; then
  echo "ERROR: ANDROID_NDK_HOME is not set or does not exist." >&2
  exit 1
fi

# Locate the NDK LLVM toolchain (host tag differs per runner OS).
NDK_TOOLCHAIN=""
for host in linux-x86_64 darwin-x86_64 windows-x86_64; do
  if [ -d "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${host}/bin" ]; then
    NDK_TOOLCHAIN="${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/${host}/bin"
    break
  fi
done
if [ -z "${NDK_TOOLCHAIN}" ]; then
  echo "ERROR: could not find the NDK LLVM toolchain under ${ANDROID_NDK_HOME}" >&2
  exit 1
fi
echo "==> NDK toolchain: ${NDK_TOOLCHAIN}"

clang_for_abi() {
  case "$1" in
    arm64-v8a)   echo "${NDK_TOOLCHAIN}/aarch64-linux-android${API}-clang" ;;
    armeabi-v7a) echo "${NDK_TOOLCHAIN}/armv7a-linux-androideabi${API}-clang" ;;
    *) echo "" ;;
  esac
}

# ---------------------------------------------------------------------------
# 1) hev-socks5-tunnel  (Android build via ndk-build)
# ---------------------------------------------------------------------------
# The plain Makefile cross-compiles lwip's UNIX port, whose fd_set typedef
# collides with Android bionic. hev ships an Android build (Android.mk +
# Application.mk, at the REPO ROOT) that configures lwip correctly. We then make
# sure we end up with a runnable executable to spawn, whatever the Android build
# emits (executable / shared lib / static lib), via a tiny wrapper around hev's
# public entry point:  int hev_socks5_tunnel_main(const char *config, int fd);
build_hev() {
  local mk_dir=""
  if [ -f "${HEV_DIR}/Android.mk" ]; then
    mk_dir="${HEV_DIR}"
  elif [ -f "${HEV_DIR}/jni/Android.mk" ]; then
    mk_dir="${HEV_DIR}/jni"
  else
    echo "ERROR: Android.mk not found in ${HEV_DIR} or ${HEV_DIR}/jni." >&2
    ls -la "${HEV_DIR}" >&2 || true
    exit 1
  fi
  local app_mk="${mk_dir}/Application.mk"
  [ -f "${app_mk}" ] || app_mk=""

  local ndkbuild="${ANDROID_NDK_HOME}/ndk-build"
  if [ ! -x "${ndkbuild}" ]; then
    echo "ERROR: ndk-build not found at ${ndkbuild}" >&2
    exit 1
  fi

  # PKGNAME makes hev's own hev-jni.c register its natives
  # (TProxyStartService/TProxyStopService/TProxyGetStats) onto OUR Kotlin
  # class studio.cluvex.aether.core.TProxyService — exactly the mechanism
  # v2rayNG uses (compile-hevtun.sh: -DPKGNAME=com/v2ray/ang/service).
  echo "==> [hev] ndk-build (${mk_dir}/Android.mk) for ${ABIS[*]} (API ${API})"
  ( cd "${HEV_DIR}" && "${ndkbuild}" \
      NDK_PROJECT_PATH="${HEV_DIR}" \
      APP_BUILD_SCRIPT="${mk_dir}/Android.mk" \
      ${app_mk:+NDK_APPLICATION_MK="${app_mk}"} \
      APP_ABI="${ABIS[*]}" \
      APP_PLATFORM="android-${API}" \
      "APP_CFLAGS=-O3 -DPKGNAME=studio/cluvex/aether/core" \
      -j"$(nproc 2>/dev/null || echo 2)" )

  # hev must run IN-PROCESS (the VpnService TUN fd is per-process), and it
  # must run on a NATIVE thread. We now ship hev's own JNI library verbatim:
  # its JNI_OnLoad registers the TProxy* natives and TProxyStartService runs
  # the tunnel event loop on a plain pthread that hev creates itself.
  #
  # ROOT-CAUSE NOTE: the previous custom wrapper (libhev.so + hev_jni.c)
  # called hev_socks5_tunnel_main directly on a Java (ART-attached) thread.
  # hev-task-system implements coroutines by swapping the thread's stack
  # pointer; doing that on a thread ART manages corrupts what the runtime
  # expects of the stack and kills the whole app with a native SIGSEGV a few
  # seconds after real traffic starts — with nothing in the Java crash log.
  # Running the loop on hev's own pthread (v2rayNG's proven mode) avoids ART
  # entirely.
  local abi libsdir out dynsyms
  for abi in "${ABIS[@]}"; do
    libsdir="${HEV_DIR}/libs/${abi}"
    out="${JNI_DIR}/${abi}/libhev-socks5-tunnel.so"
    mkdir -p "${JNI_DIR}/${abi}"
    if [ ! -f "${libsdir}/libhev-socks5-tunnel.so" ]; then
      echo "ERROR: [${abi}] ndk-build did not produce libhev-socks5-tunnel.so" >&2
      ls -la "${libsdir}" 2>/dev/null >&2 || true
      exit 1
    fi
    cp "${libsdir}/libhev-socks5-tunnel.so" "${out}"
    # Never ship stale artifacts from the old wrapper approach.
    rm -f "${JNI_DIR}/${abi}/libhev.so" "${JNI_DIR}/${abi}/libhevcore.so"

    # ---- Hard verification: NEVER ship a library that cannot register. ----
    dynsyms="$("${NDK_TOOLCHAIN}/llvm-nm" --dynamic --defined-only "${out}" 2>/dev/null || true)"
    if ! echo "${dynsyms}" | grep -qw 'JNI_OnLoad'; then
      echo "ERROR: [${abi}] libhev-socks5-tunnel.so lacks JNI_OnLoad — hev-jni.c was not compiled in." >&2
      exit 1
    fi
    if ! echo "${dynsyms}" | grep -qw 'hev_socks5_tunnel_main'; then
      echo "ERROR: [${abi}] libhev-socks5-tunnel.so lacks hev_socks5_tunnel_main." >&2
      exit 1
    fi
    if ! grep -aq 'studio/cluvex/aether/core/TProxyService' "${out}"; then
      echo "ERROR: [${abi}] PKGNAME not applied — JNI_OnLoad would register natives on the wrong class." >&2
      exit 1
    fi
    echo "    [${abi}] libhev-socks5-tunnel.so verified: JNI_OnLoad + hev_socks5_tunnel_main + PKGNAME OK"
    "${NDK_TOOLCHAIN}/llvm-readelf" -d "${out}" 2>/dev/null | grep NEEDED || true
  done
}

# ---------------------------------------------------------------------------
# 2) Aether engine  (Rust, cargo-ndk)
# ---------------------------------------------------------------------------
# The repo root has NO Cargo.toml. The binary crate lives in a subdirectory
# (e.g. aether/) next to the vendored quiche/ library. Detect it: pick the
# crate that has src/main.rs and is NOT under quiche/.
detect_aether_crate() {
  if [ -f "${AETHER_SRC}/aether/Cargo.toml" ] && [ -f "${AETHER_SRC}/aether/src/main.rs" ]; then
    echo "${AETHER_SRC}/aether"
    return 0
  fi
  local toml d
  while IFS= read -r toml; do
    d="$(dirname "${toml}")"
    case "${d}" in
      *quiche*) continue ;;
    esac
    if [ -f "${d}/src/main.rs" ]; then
      echo "${d}"
      return 0
    fi
  done < <(find "${AETHER_SRC}" -name Cargo.toml -not -path '*/target/*' | sort)
  return 1
}

build_aether() {
  local crate
  crate="$(detect_aether_crate || true)"
  if [ -z "${crate}" ]; then
    echo "ERROR: could not find the Aether binary crate (a Cargo.toml with src/main.rs)." >&2
    echo "Manifests found:" >&2
    find "${AETHER_SRC}" -name Cargo.toml -not -path '*/target/*' >&2 || true
    exit 1
  fi
  echo "==> [aether] binary crate: ${crate}"

  export CARGO_TARGET_DIR="${AETHER_SRC}/target"

  local bin_name
  bin_name="$(grep -m1 -E '^name[[:space:]]*=' "${crate}/Cargo.toml" \
    | sed -E 's/.*=[[:space:]]*"([^"]+)".*/\1/' || true)"
  echo "    crate name (best-effort): ${bin_name:-<auto-detect>}"

  build_aether_abi() {
    local abi="$1" triple="$2"
    echo "==> [aether] building for ${abi} (${triple}, API ${API})"
    ( cd "${crate}" && ANDROID_NDK_ROOT="${ANDROID_NDK_HOME}" cargo ndk -t "${abi}" --platform "${API}" build --release )

    local reldir="${CARGO_TARGET_DIR}/${triple}/release"
    local artifact=""
    if [ -n "${bin_name}" ] && [ -x "${reldir}/${bin_name}" ]; then
      artifact="${reldir}/${bin_name}"
    else
      artifact="$(find "${reldir}" -maxdepth 1 -type f -perm -u+x \
        ! -name '*.so' ! -name '*.d' ! -name '*.rlib' ! -name '*.rmeta' \
        2>/dev/null | head -n1)"
    fi
    if [ -z "${artifact}" ] || [ ! -f "${artifact}" ]; then
      echo "ERROR: could not locate a built Aether executable in ${reldir}" >&2
      ls -la "${reldir}" 2>/dev/null >&2 || true
      exit 1
    fi

    mkdir -p "${JNI_DIR}/${abi}"
    cp "${artifact}" "${JNI_DIR}/${abi}/libaether.so"
    "${NDK_TOOLCHAIN}/llvm-strip" "${JNI_DIR}/${abi}/libaether.so" 2>/dev/null || true
    echo "    installed libaether.so for ${abi}"
  }

  build_aether_abi "arm64-v8a"   "aarch64-linux-android"
  build_aether_abi "armeabi-v7a" "armv7-linux-androideabi"
}

case "${TARGET}" in
  hev)    build_hev ;;
  aether) build_aether ;;
  all)    build_hev; build_aether ;;
  *) echo "Usage: build-natives.sh [hev|aether|all]" >&2; exit 2 ;;
esac

echo "==> Done (${TARGET}). Installed libs:"
find "${JNI_DIR}" -type f -name '*.so' -exec ls -la {} + 2>/dev/null || true
