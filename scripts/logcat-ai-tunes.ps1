# Logcat filtrado (usa platform-tools del SDK aunque adb no esté en PATH).
# Solo benchmark/tiempos:  .\scripts\logcat-ai-tunes.ps1 -BenchOnly
param([switch] $BenchOnly)
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
       elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
       else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Error "No se encuentra $adb" }
if ($BenchOnly) {
    & $adb logcat -v time "*:S" "AITUNES_MODEL_BENCH:I"
} else {
    # *:S silencia el resto; prioridades explicitas evitan sorpresas con adb en Windows.
    & $adb logcat -v time "*:S" "AITUNES_MODEL_BENCH:I" "AiTunesLlama:I" "LlamaEngine:I" "ModelManager:I"
}
