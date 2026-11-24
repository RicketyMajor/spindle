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

## ADR-005 — Etiquetas con el AAR de TagLib de Kyant0

- **Contexto:** SPEC 5.9 pide TagLib para `flac`, `mp3`, `opus`, `ogg`, `m4a` (AAC y ALAC) y `wav`, y deja elegir la forma de integrarlo. Auxio compila su propio JNI (`musikr`, GPLv3) y PixelPlayer usa el AAR de Kyant0.
- **Decisión:**
  - `io.github.kyant0:taglib:1.0.6` (Apache-2.0, la última versión en Maven Central), solo con `abiFilters += "arm64-v8a"`.
  - Lectura como PixelPlayer: `ParcelFileDescriptor.open(file, MODE_READ_ONLY)` y `TagLib.getMetadata(fd.dup().detachFd(), readPictures = false)` + `TagLib.getAudioProperties(...)`.
  - De TagLib salen las etiquetas (`propertyMap`) y la duración (en **ms**), el bitrate, la frecuencia y los canales. No se leen imágenes en F1.
  - Prueba en el teléfono (2026-11-09, build debug, un hilo): 5.012 archivos sintéticos de los 7 formatos, 0 errores, **3,3 ms por archivo** con propiedades, mapa e imágenes. Leyó los géneros múltiples `rock;electrónica`, `YT_ID`, `COMPOSER` y `COMPILATION`.
- **Alternativas:**
  - *JNI propio o `musikr` de Auxio:* da también el códec, pero exige NDK, CMake y el submódulo de TagLib.
  - *jaudiotagger:* más lento y sin Opus nativo.
  - *MediaStore:* lo descarta SPEC 5.9 (ADR-001).
- **Consecuencias:**
  - El APK crece unos 4 MB (25 MB en debug). Para usar un emulador x86_64 hay que agregar la ABI.
  - El AAR **no** entrega el códec ni la profundidad de bits (ADR-006).
  - La lectura con TagLib es nativa: no corre en las pruebas de la JVM y se verifica en el teléfono.

## ADR-006 — Códec y profundidad de bits desde una lectura propia del encabezado

- **Contexto:** el indicador técnico (SPEC 5.3) necesita el códec (en `.m4a` puede ser AAC o ALAC) y la profundidad de bits de FLAC, WAV y ALAC. TagLib (ADR-005) no los entrega. El presupuesto del primer escaneo es 12 ms por pista para todo (5.000 pistas en < 60 s).
- **Decisión:** `library/AudioHeader.kt`, Kotlin puro con `RandomAccessFile`:
  - FLAC: el bloque STREAMINFO, saltando un ID3 inicial si lo hay;
  - WAV: el chunk `fmt `;
  - MP4: se recorren las cajas hasta `moov/trak/mdia/minf/stbl/stsd`. `alac` → ALAC, con la profundidad del byte 5 de su cookie; `mp4a` → AAC. `moov` puede estar antes o después de `mdat`;
  - el resto, por extensión: `mp3` → MP3, `opus` → OPUS, `ogg` → VORBIS.

  Los formatos con pérdida muestran el bitrate de TagLib. Se prueba en la JVM con bytes armados en el test.
- **Alternativas** (medidas en el teléfono, mismos 5.012 archivos):
  - *`MediaExtractor`:* **9,3 ms por archivo**, y en HyperOS reporta FLAC como `audio/raw`, así que igual hay que mirar la extensión. Su `pcm-encoding` es la salida del decodificador, no la profundidad del archivo.
  - *`MediaMetadataRetriever`:* **15,8 ms**, pide API 31 (minSdk es 30), y su mime es el del contenedor: `audio/mp4` para AAC y ALAC.
  - *`MetadataRetriever` de Media3:* prepara una fuente por archivo; caro para un escaneo.
  - *Sin profundidad de bits:* lo más simple, pero `[FLAC 44.1kHz]` no distingue 16 de 24 bits, y ALAC se ve igual que AAC.
- **Consecuencias:**
  - Son unas 80 líneas propias, de menos de 0,5 ms por archivo.
  - Límite: un `.ogg` se rotula como Vorbis por su extensión. Opus dentro de `.ogg` saldría mal, pero Station lo escribe como `.opus`.
  - Si el encabezado no se entiende, el indicador queda sin bits (`[FLAC 44.1kHz]`).
