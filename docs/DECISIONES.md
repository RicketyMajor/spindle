# Decisiones técnicas (ADR)

Formato corto: contexto · decisión · alternativas · consecuencias. Las más nuevas, abajo.

---

## ADR-001 — Acceso a archivos con `MANAGE_EXTERNAL_STORAGE`

- **Contexto:** Spindle tiene que recorrer `Music/Spindle/Biblioteca/` rápido, leer `notes.md` y `cover.jpg`, y escribir en `Music/Spindle/Datos/`. Es una app personal: no se publica en Play Store.
- **Decisión:** pedir "Acceso a todos los archivos" (`MANAGE_EXTERNAL_STORAGE`) y usar la API de archivos (`java.io.File`) directo sobre la ruta.
- **Alternativas:**
  - *MediaStore:* no expone `notes.md` ni `cover.jpg`, ni la profundidad de bits ni la frecuencia de muestreo, y depende del escáner del sistema.
  - *Storage Access Framework:* el usuario elige la carpeta una vez. Es viable, pero recorrer `DocumentFile` es mucho más lento para miles de archivos, y obliga a pasar URIs `content://` a TagLib en vez de rutas.
- **Consecuencias:**
  - La app no sería aceptada en Play Store sin una justificación.
  - El permiso se concede a mano en ajustes, o por ADB: `adb shell appops set dev.ricketymajor.spindle MANAGE_EXTERNAL_STORAGE allow`.
  - Si algún día se distribuye, migrar a SAF.

## ADR-002 — Station se instala con `uv tool`

- **Contexto:** la spec pide instalar Station como herramienta, con `pipx` o `uv tool`. En el PC está uv 0.13.7 y no está pipx.
- **Decisión:** `uv tool install --editable ./station` para el uso diario, y `uv run --project station` para desarrollo y pruebas.
- **Alternativas:** pipx, que obligaría a instalar otra herramienta y no trae un lockfile.
- **Consecuencias:** `station/uv.lock` va versionado. En el notebook con Linux se usa el mismo comando.

## ADR-003 — Decisiones abiertas E4–E8: valores por defecto confirmados

- **Contexto:** la sección 12 de la spec deja E4–E8 abiertas, cada una con un valor por defecto.
- **Decisión (confirmada por el usuario el 2026-11-07):**
  - E4: tras `devolver` un disco, el cajón queda deshabilitado hasta que se reproduzca algo elegido a mano.
  - E5: la portada pequeña en "sonando" está desactivada y se puede activar en ajustes.
  - E6: `notes.md` no se edita desde el teléfono en v1.
  - E7: el pedido de texto pregunta en modo interactivo; en el lote de madrugada elige la canción oficial con álbum, o la marca como "requiere revisión".
  - E8: ReplayGain sí, en F6.
- **Alternativas:** ninguna se planteó.
- **Consecuencias:** no hay que volver a preguntarlas al llegar a cada fase.

## ADR-004 — Toolchain y paquete compartidos con Bare

- **Contexto:** Spindle y Bare son apps hermanas. Bare envía pedidos a Spindle con un `Intent` explícito, así que necesita su paquete.
- **Decisión:**
  - `applicationId` `dev.ricketymajor.spindle` (Bare: `dev.ricketymajor.bare`).
  - AGP 9.5.1 (Kotlin 2.4.21 integrado, sin el plugin `kotlin-android`), Gradle 9.9.0, Compose BOM 2026.09.00.
  - compileSdk/targetSdk 37 (la última estable al 2026-11-07), minSdk 30, JDK 21 (el JBR de Android Studio).
  - Media3 1.11.1 cuando entre, en F2.
  - Dependencias: solo las que se usan, a medida que se usan.
- **Alternativas:** fijar cada app por separado. Se descartó para no resolver dos veces los mismos problemas de build.
- **Consecuencias:** los cambios de versiones se coordinan entre los dos proyectos.
