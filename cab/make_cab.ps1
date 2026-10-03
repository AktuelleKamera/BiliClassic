# =====================================================================
# make_cab.ps1 - build Meizu M8 install package (CAB)
# Usage: powershell -ExecutionPolicy Bypass -File make_cab.ps1 [Release|Debug]
# Requires: BiliClassic-M8.exe already built by vcbuild for that config.
# =====================================================================
param([string]$Config = "Release")
$ErrorActionPreference = "Stop"

$cabDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$root   = Split-Path -Parent $cabDir
$exeSrc = Join-Path $root ("BiliClassic-M8\M8SDK (ARMV4I)\{0}\BiliClassic-M8.exe" -f $Config)
$cabwiz = "L:\Software\Microsoft Visual Studio 9.0\SmartDevices\SDK\SDKTools\cabwiz.exe"
$inf    = Join-Path $cabDir "BiliClassic-M8.inf"
$err    = Join-Path $cabDir "cabwiz_err.txt"
$outCab = Join-Path $cabDir "BiliClassic-M8.CAB"
$postxml = Join-Path $cabDir "bili_postxml.xml"

if (-not (Test-Path $exeSrc)) { throw "exe not found: $exeSrc (build $Config first)" }
if (-not (Test-Path $cabwiz)) { throw "cabwiz.exe not found: $cabwiz" }

Copy-Item $exeSrc (Join-Path $cabDir "BiliClassic-M8.exe") -Force

$pngSrc = Join-Path $root "BiliClassic-M8\BiliClassic.png"
if (Test-Path $pngSrc) { Copy-Item $pngSrc (Join-Path $cabDir "BiliClassic.png") -Force }

$ttfSrc = Join-Path $root "BiliClassic-M8\assets\danmaku.ttf"
if (Test-Path $ttfSrc) { Copy-Item $ttfSrc (Join-Path $cabDir "danmaku.ttf") -Force }

Push-Location $cabDir
try {
    if (Test-Path $postxml) {
        & $cabwiz $inf /dest $cabDir /err $err /postxml $postxml /compress | Out-Null
    } else {
        & $cabwiz $inf /dest $cabDir /err $err /compress | Out-Null
    }
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -ne 0) {
    $msg = ""
    if (Test-Path $err) {
        $b = [System.IO.File]::ReadAllBytes($err)
        if ($b.Length -ge 2 -and $b[0] -eq 0xFF) { $msg = [System.Text.Encoding]::Unicode.GetString($b, 2, $b.Length - 2) }
        else { $msg = [System.Text.Encoding]::Default.GetString($b) }
    }
    throw "cabwiz failed (exit $code)`n$msg"
}
if (-not (Test-Path $outCab)) { throw "CAB not generated" }

"CAB OK: {0} ({1} KB)" -f $outCab, [int]((Get-Item $outCab).Length / 1KB)
