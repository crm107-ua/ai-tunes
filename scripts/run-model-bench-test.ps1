# Ejecuta ModelInferenceTimingInstrumentedTest. Arranca el emulador si adb no ve ningún dispositivo.
# Uso (desde la raíz del repo):
#   .\scripts\run-model-bench-test.ps1
#   .\scripts\run-model-bench-test.ps1 -AvdName Pixel_8

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
        Write-Error "No se encuentra: $p. Revisa ANDROID_SDK_ROOT."
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
        Write-Error "No hay dispositivo ni AVD. Conecta USB con depuracion o crea un AVD en Android Studio."
    }

    $pick = $AvdName
    if (-not $pick) {
        if ($avds -contains "Pixel_8") { $pick = "Pixel_8" }
        else { $pick = $avds[0] }
    }
    if ($avds -notcontains $pick) {
        Write-Error "AVD '$pick' no existe. Disponibles: $($avds -join ', ')"
    }

    Write-Host "No hay dispositivo en adb. Iniciando emulador: $pick ..."
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $pick, "-netdelay", "none", "-netspeed", "full") | Out-Null

    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    while (-not (Test-HasRunningDevice)) {
        if ((Get-Date) -gt $deadline) {
            Write-Error "Tiempo de espera: el emulador no aparecio en adb."
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "Dispositivo listo: $(Get-AdbDevices)"
}

if (-not $PSScriptRoot) { $PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path }
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

Write-Host "connectedDebugAndroidTest (ModelInferenceTimingInstrumentedTest)..."
Write-Host "Nota: la consola de Gradle puede quedarse en EXECUTING ~98% minutos sin nuevas lineas; el test sigue en el dispositivo."
Write-Host "      Abre otra ventana PowerShell y ejecuta: .\scripts\logcat-ai-tunes.ps1  (o: adb logcat -s AITUNES_MODEL_BENCH:I)"
Write-Host ""
# -PemuAbiOnly=true: solo lib x86_64 en el APK (evita fallos de install / espacio en emulador).
& .\gradlew.bat ":app:connectedDebugAndroidTest" `
    "-PemuAbiOnly=true" `
    "-Pandroid.testInstrumentationRunnerArguments.class=com.aitunes.app.ModelInferenceTimingInstrumentedTest"
exit $LASTEXITCODE
