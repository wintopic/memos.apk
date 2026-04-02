#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ANDROID_PROJECT_DIR="$REPO_ROOT/android"
ANDROID_LIB_DIR="$ANDROID_PROJECT_DIR/app/libs"
X_MOBILE_VERSION="v0.0.0-20260217195705-b56b3793a9c4"

command -v go >/dev/null 2>&1 || { echo "Go 1.26+ is required."; exit 1; }
command -v npm >/dev/null 2>&1 || { echo "Node.js 24+ with npm is required."; exit 1; }

GOBIN="$(go env GOBIN)"

if [[ -z "$GOBIN" ]]; then
  GOBIN="$(go env GOPATH)/bin"
fi

export PATH="$GOBIN:$PATH"

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" ]]; then
  echo "Set ANDROID_HOME or ANDROID_SDK_ROOT before building Android bindings."
  exit 1
fi

mkdir -p "$ANDROID_LIB_DIR"

pushd "$REPO_ROOT" >/dev/null

GO_MOD_BACKUP="$(mktemp)"
GO_SUM_BACKUP="$(mktemp)"
cp "$REPO_ROOT/go.mod" "$GO_MOD_BACKUP"
if [[ -f "$REPO_ROOT/go.sum" ]]; then
  cp "$REPO_ROOT/go.sum" "$GO_SUM_BACKUP"
else
  : > "$GO_SUM_BACKUP"
fi

restore_go_files() {
  cp "$GO_MOD_BACKUP" "$REPO_ROOT/go.mod"
  cp "$GO_SUM_BACKUP" "$REPO_ROOT/go.sum"
  rm -f "$GO_MOD_BACKUP" "$GO_SUM_BACKUP"
}

trap restore_go_files EXIT

echo "==> Building frontend bundle"
pushd "$REPO_ROOT/web" >/dev/null
npx --yes pnpm@10 install --frozen-lockfile
npx --yes pnpm@10 release
popd >/dev/null

echo "==> Installing gomobile toolchain"
go install "golang.org/x/mobile/cmd/gomobile@${X_MOBILE_VERSION}"
go install "golang.org/x/mobile/cmd/gobind@${X_MOBILE_VERSION}"

echo "==> Adding x/mobile bind dependency for module-mode gomobile"
go get -d "golang.org/x/mobile/bind@${X_MOBILE_VERSION}"

echo "==> Initializing gomobile"
gomobile init

echo "==> Building Android AAR"
export CGO_ENABLED=1
gomobile bind \
  -target=android \
  -androidapi=28 \
  -tags=android \
  -javapkg=com.usememos.mobile \
  -o "$ANDROID_LIB_DIR/memosmobile.aar" \
  ./mobile/memosmobile

echo "==> Done"
echo "Open $ANDROID_PROJECT_DIR in Android Studio and run the app."

popd >/dev/null
