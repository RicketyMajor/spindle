# Dispositivo de prueba

Detectado por ADB el 2026-11-07. La spec hablaba de un Redmi Note 10 u 11; el teléfono real es este:

| Propiedad | Valor |
|---|---|
| Modelo | Redmi 10 5G · `22041219NY` · codename `thunder` |
| Android | 14 (API 34) · parche de seguridad 2024-08-01 |
| Capa | HyperOS `OS1.0.6.0.ULSMIXM` (`ro.miui.ui.version.name=V816`) |
| ABI | arm64-v8a |
| Pantalla | 1080×2408 a 440 dpi · navegación con 3 botones |

## Ajustes necesarios (HyperOS)

Los mismos que pide Bare. Sin ellos, HyperOS puede cortar la música con la pantalla apagada o bloquear la instalación.

1. **Opciones de desarrollador › Instalar vía USB:** activado. Pide iniciar sesión con la cuenta Mi; sin esto, `installDebug` falla con `INSTALL_FAILED_USER_RESTRICTED`.
2. **Opciones de desarrollador › Depuración USB (ajustes de seguridad):** activado.
3. **Ajustes › Apps › Spindle › Inicio automático:** activado.
4. **Ajustes › Apps › Spindle › Ahorro de batería:** "Sin restricciones".
5. **Recientes:** mantener presionada la tarjeta de Spindle › candado.
6. **Acceso a todos los archivos:** lo concede `scripts/setup-device.ps1`; si no, se hace a mano desde la app.

`scripts/setup-device.ps1` hace lo automatizable y muestra el resto como lista.
