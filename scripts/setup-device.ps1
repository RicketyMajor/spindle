<#
.SYNOPSIS
  Prepares the phone for Spindle: installs the debug APK, grants all-files access
  and optionally pushes the synthetic test library. Prints the manual HyperOS steps.

.EXAMPLE
  .\scripts\setup-device.ps1                # install + grant
  .\scripts\setup-device.ps1 -PushTestLib   # also copy testlib\Biblioteca to the phone
#>
param([switch]$PushTestLib)

$ErrorActionPreference = 'Stop'
$pkg = 'dev.ricketymajor.spindle'
$root = Split-Path $PSScriptRoot -Parent
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }

$devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\tdevice$' }
if (-not $devices) { throw 'No hay teléfono conectado (o falta autorizar la depuración USB).' }
$model = (& $adb shell getprop ro.product.marketname).Trim()
Write-Host "Teléfono: $model ($((& $adb shell getprop ro.product.model).Trim()))"

$apk = Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "Falta el APK. Compila primero: cd android; .\gradlew assembleDebug" }
Write-Host 'Instalando... acepta el aviso en el teléfono si aparece.'
# PS 5.1 turns native stderr into terminating errors under 'Stop'; capture it as text instead.
$ErrorActionPreference = 'Continue'
$out = & $adb install -r $apk 2>&1 | Out-String
$ErrorActionPreference = 'Stop'
if ($out -match 'INSTALL_FAILED_USER_RESTRICTED') {
    throw 'HyperOS bloqueó la instalación: activa "Instalar vía USB" en Opciones de desarrollador y acepta el aviso en el teléfono.'
}
if ($out -notmatch 'Success') { throw "adb install falló:`n$out" }
Write-Host 'Instalado.'

& $adb shell appops set $pkg MANAGE_EXTERNAL_STORAGE allow
Write-Host 'Acceso a todos los archivos: concedido.'

if ($PushTestLib) {
    $lib = Join-Path $root 'testlib\Biblioteca'
    if (-not (Test-Path $lib)) { throw 'Falta la biblioteca de prueba: python scripts\make_testlib.py' }
    & $adb shell rm -rf /sdcard/Music/Spindle/Biblioteca
    & $adb shell mkdir -p /sdcard/Music/Spindle
    & $adb push $lib /sdcard/Music/Spindle/ | Select-Object -Last 1
}

Write-Host @"

Ajustes manuales (una sola vez, ver docs/DISPOSITIVO.md):
  1. Opciones de desarrollador > Instalar vía USB: activado (pide cuenta Mi)
  2. Opciones de desarrollador > Depuración USB (ajustes de seguridad): activado
  3. Ajustes > Apps > Spindle > Inicio automático: activado
  4. Ajustes > Apps > Spindle > Ahorro de batería: Sin restricciones
  5. Recientes: mantener presionada la tarjeta de Spindle > candado
"@
