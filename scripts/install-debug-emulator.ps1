# Compila, instala debug y arranca MainActivity.
# Arranca el emulador si no hay dispositivo (usa ANDROID_SDK_ROOT / ANDROID_HOME / Sdk por defecto).
# Uso (desde la raíz del repo):
#   .\scripts\install-debug-emulator.ps1
# Con AVD concreto:
#   .\scripts\install-debug-emulator.ps1 -AvdName Pixel_8

param(
    [string] $AvdName = "",
    [int] $WaitSeconds = 120
)

$ErrorActionPreference = "Stop"

$sdkRoot =
    if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
    elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
    else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }

$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $sdkRoot "emulator\emulator.exe"

foreach ($p in @($adb, $emulator)) {
    if (-not (Test-Path $p)) {
        Write-Error "No se encuentra: $p. Revisa ANDROID_SDK_ROOT o instala Android SDK Platform-Tools / Emulator."
    }
}

function Get-AdbDevices {
    & $adb devices 2>$null | Where-Object { $_ -match "\tdevice$" }
}

function Test-HasRunningDevice {
    return [bool](Get-AdbDevices)
}

function Get-AvdNames {
    $lines = & $emulator -list-avds 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $lines) { return @() }
    return @($lines | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

if (-not (Test-HasRunningDevice)) {
    $avds = Get-AvdNames
    if ($avds.Count -eq 0) {
        Write-Error "No hay AVD creados. Crea uno en Android Studio (Device Manager)."
    }

    $pick = $AvdName
    if (-not $pick) {
        if ($avds -contains "Pixel_8") { $pick = "Pixel_8" }
        else { $pick = $avds[0] }
    }
    if ($avds -notcontains $pick) {
        Write-Error "AVD '$pick' no existe. Disponibles: $($avds -join ', ')"
    }

    Write-Host "Iniciando emulador: $pick ..."
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $pick, "-netdelay", "none", "-netspeed", "full") | Out-Null

    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    while (-not (Test-HasRunningDevice)) {
        if ((Get-Date) -gt $deadline) {
            Write-Error "Tiempo de espera: el emulador no apareció en adb."
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "Dispositivo listo: $(Get-AdbDevices)"
}

if (-not $PSScriptRoot) { $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

Write-Host "installDebug..."
& (Join-Path $repoRoot "gradlew.bat") installDebug
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Abriendo MainActivity..."
& $adb shell am start -n com.aitunes.app/.ui.MainActivity
exit $LASTEXITCODE
