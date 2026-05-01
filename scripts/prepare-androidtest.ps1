# Reinicia adb y lista dispositivos. Si install falla: borrar datos del AVD o subir almacenamiento interno.
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
       elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
       else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$adb = Join-Path $sdk "platform-tools\adb.exe"
if (-not (Test-Path $adb)) { Write-Error "No adb en $adb"; exit 1 }

Write-Host "adb kill-server / start-server..."
& $adb kill-server 2>$null
Start-Sleep -Milliseconds 500
& $adb start-server
& $adb devices -l
Write-Host ""
Write-Host "Si no aparece ningun dispositivo en estado 'device': arranca un AVD en Android Studio o ejecuta desde la raiz del repo:"
Write-Host "  .\scripts\run-model-bench-test.ps1"
Write-Host "(ese script arranca el emulador si adb esta vacio)."
Write-Host ""
Write-Host "Si install falla con FAILED_SESSION_CREATE / espacio: AVD Manager -> Wipe Data, o mas Internal Storage."
Write-Host "Prueba instalacion manual:"
Write-Host "  & `"$adb`" install -r app\build\outputs\apk\debug\app-debug.apk"
