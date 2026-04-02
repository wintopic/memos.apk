$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$androidProjectDir = Join-Path $repoRoot "android"
$androidLibDir = Join-Path $androidProjectDir "app\libs"
$outputAar = Join-Path $androidLibDir "memosmobile.aar"
$xMobileVersion = "v0.0.0-20260217195705-b56b3793a9c4"

if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
  throw "Go is required. Install Go 1.26+ and retry."
}

$goBin = (go env GOBIN).Trim()
if ([string]::IsNullOrWhiteSpace($goBin)) {
  $goBin = Join-Path ((go env GOPATH).Trim()) "bin"
}
$env:Path = "$goBin;$env:Path"

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
  throw "npm is required. Install Node.js 24+ and retry."
}

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
  throw "Set ANDROID_HOME or ANDROID_SDK_ROOT before building Android bindings."
}

New-Item -ItemType Directory -Force -Path $androidLibDir | Out-Null

$goModBackup = Join-Path $env:TEMP ("memos-go-mod-" + [guid]::NewGuid().ToString() + ".bak")
$goSumBackup = Join-Path $env:TEMP ("memos-go-sum-" + [guid]::NewGuid().ToString() + ".bak")
Copy-Item -LiteralPath (Join-Path $repoRoot "go.mod") -Destination $goModBackup
if (Test-Path -LiteralPath (Join-Path $repoRoot "go.sum")) {
  Copy-Item -LiteralPath (Join-Path $repoRoot "go.sum") -Destination $goSumBackup
} else {
  New-Item -ItemType File -Path $goSumBackup | Out-Null
}

Push-Location $repoRoot
try {
  Write-Host "==> Building frontend bundle"
  Push-Location (Join-Path $repoRoot "web")
  try {
    & npx --yes "pnpm@10" install --frozen-lockfile
    & npx --yes "pnpm@10" release
  } finally {
    Pop-Location
  }

  Write-Host "==> Installing gomobile toolchain"
  go install "golang.org/x/mobile/cmd/gomobile@$xMobileVersion"
  go install "golang.org/x/mobile/cmd/gobind@$xMobileVersion"

  Write-Host "==> Adding x/mobile bind dependency for module-mode gomobile"
  go get -d "golang.org/x/mobile/bind@$xMobileVersion"

  Write-Host "==> Initializing gomobile"
  gomobile init

  Write-Host "==> Building Android AAR"
  $env:CGO_ENABLED = "1"
  gomobile bind `
    -target=android `
    -androidapi=28 `
    -tags=android `
    -javapkg=com.usememos.mobile `
    -o $outputAar `
    ./mobile/memosmobile

  Write-Host "==> Done"
  Write-Host "Open $androidProjectDir in Android Studio and run the app."
} finally {
  Copy-Item -LiteralPath $goModBackup -Destination (Join-Path $repoRoot "go.mod") -Force
  Copy-Item -LiteralPath $goSumBackup -Destination (Join-Path $repoRoot "go.sum") -Force
  Remove-Item -LiteralPath $goModBackup,$goSumBackup -Force -ErrorAction SilentlyContinue
  Pop-Location
}
