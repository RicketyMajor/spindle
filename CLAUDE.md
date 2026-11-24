# Spindle

Reproductor de música local para Android, sin algoritmos (estilo iPod, estética de terminal), más **Spindle Station**: la CLI de PC que baja, etiqueta y organiza la música. La fuente de verdad es [`docs/SPEC.md`](docs/SPEC.md): léela antes de cambiar el comportamiento.

## Estructura

```
android/     App Spindle: Kotlin + Compose + Media3 + Room (dev.ricketymajor.spindle)
station/     Spindle Station: CLI en Python 3.12+ (comando `station`)
docs/        SPEC.md · DECISIONES.md (ADR) · DISPOSITIVO.md · referencias.md
scripts/     setup-device.ps1 y utilidades
refs/        Repos de referencia clonados (git-ignored; no se compilan)
```

## Comandos

```sh
# Android (desde android/; JAVA_HOME = JBR de Android Studio)
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"   # PowerShell: $env:JAVA_HOME = "..."
./gradlew assembleDebug                 # APK en app/build/outputs/apk/debug/
./gradlew testDebugUnitTest             # pruebas JVM

# Teléfono (desde la raíz, PowerShell)
.\scripts\setup-device.ps1              # instala el APK y concede el acceso a archivos
.\scripts\setup-device.ps1 -PushTestLib # además copia testlib\Biblioteca a /sdcard/Music/Spindle/

# Biblioteca de prueba (requiere ffmpeg)
python scripts/make_testlib.py          # genera testlib/Biblioteca (git-ignored)
python scripts/make_testlib.py --grande # testlib/Grande: 5.000 pistas para medir el escaneo

# Station (desde station/)
uv run pytest                           # pruebas
uv run station --version
uv tool install --editable .            # instala el comando `station` (ADR-002)
```

**adb desde Git Bash:** anteponer `MSYS_NO_PATHCONV=1`. Si no, `/sdcard/...` se convierte en `C:/Program Files/Git/sdcard/...`. Problemas de HyperOS con la instalación: `docs/DISPOSITIVO.md`.

**Plan B si Syncthing falla:** `adb push <Biblioteca> /sdcard/Music/Spindle/`, o copiar la carpeta por USB.

**Referencias:** `git clone --depth 1` de los repos de `docs/referencias.md` en `refs/`.

## Convenciones

- Interfaz y mensajes de la CLI en español (Chile). Código, identificadores, comentarios y commits en inglés.
- Commits: Conventional Commits, chicos (uno por hito), sin trailers.
- Decisiones técnicas no triviales → `docs/DECISIONES.md` (contexto · decisión · alternativas · consecuencias).
- Código reutilizado de `refs/` → conservar su aviso de copyright y registrarlo en `THIRD_PARTY_NOTICES.md`. Del código AGPL, sin licencia o con licencia sin verificar, solo se toman ideas.
- Android: minSdk 30, Material 3 solo como base técnica (los componentes visibles van en `ui/tui`), DI manual, sin Hilt.
- Gradle con el JDK de Android Studio (`C:\Program Files\Android\Android Studio\jbr`, 21). El `java` del PATH es el 23.
- Lenguaje visual compartido con Bare: fondo `#000000`, texto `#FFFFFF`, secundario `#8A8A8A`, acento `#FFB000`; JetBrains Mono.
