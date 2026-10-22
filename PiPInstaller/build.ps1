[CmdletBinding()]
param([switch]$Check)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
    throw 'Cần WSL với JDK 17 và Android SDK, hoặc dùng GitHub Actions. Xem README.md.'
}
Push-Location $root
try {
    # WSLENV forwards only the named signing settings; values are never printed.
    $names = @('THT_SIGN_KEYSTORE_BASE64', 'THT_SIGN_STORE_PASSWORD', 'THT_SIGN_KEY_ALIAS', 'THT_SIGN_KEY_PASSWORD', 'THT_SIGN_CERT_SHA256')
    $previous = $env:WSLENV
    $env:WSLENV = (@($previous) + $names | Where-Object { $_ }) -join ':'
    try {
        if ($Check) { & wsl.exe bash scripts/build.sh --check }
        else { & wsl.exe bash scripts/build.sh }
        if ($LASTEXITCODE -ne 0) { throw 'Biên dịch thất bại. Xem thông báo phía trên.' }
    } finally { $env:WSLENV = $previous }
} finally { Pop-Location }
