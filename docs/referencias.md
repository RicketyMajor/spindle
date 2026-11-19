# Referencias y plan de reutilización

Los repos se clonan con `git clone --depth 1` en `refs/`, que está en el gitignore. No se compilan ni son submódulos. Cada archivo copiado conserva su cabecera de copyright y se registra en `THIRD_PARTY_NOTICES.md`.

## Repos (clonados el 2026-11-07)

| Repo | Commit | Último commit | Licencia | Uso |
|---|---|---|---|---|
| [OxygenCobalt/Auxio](https://github.com/OxygenCobalt/Auxio) | `f243bce` | 2026-11-01 | GPL-3.0 | Copiar/adaptar |
| [FoedusProgramme/Gramophone](https://github.com/FoedusProgramme/Gramophone) | `55b5ba3` | 2026-10-11 | GPL-3.0 | Copiar/adaptar |
| [PixelPlayerHQ/PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) | `a1f8fce` | 2026-11-07 | GPL-3.0 | Copiar/adaptar |
| [zyrouge/symphony](https://github.com/zyrouge/symphony) | `dd04b87` | 2025-06-14 | **AGPL-3.0** | **Solo ideas** |
| [mgoltzsche/beets-ytimport](https://github.com/mgoltzsche/beets-ytimport) | `68b7b29` | 2026-07-05 | Apache-2.0 | Dependencia de Station (se instala, no se copia) |

Sin clonar, solo como referencia en PyPI: [beets-yt-dlp](https://pypi.org/project/beets-yt-dlp/) y [ytbdl](https://pypi.org/project/ytbdl/). Antes de mirar su código hay que verificar la licencia.

## Mapa por módulo

### `library`: escáner y etiquetas (F1)

| Necesidad | Referencia | Qué tomar |
|---|---|---|
| Lector de TagLib | **PixelPlayer** `data/media/AudioMetadataReader.kt` (368 líneas), con la dependencia `io.github.kyant0:taglib:1.0.6` (Apache-2.0, AAR en Maven Central) | **Propuesta:** usar el AAR de Kyant0. Trae TagLib ya compilado para arm64, así que **compila en Windows sin NDK ni scripts**. Se lee con `TagLib.getMetadata(fd)`, `getAudioProperties(fd)` y `getPictures(fd)` sobre un descriptor de archivo. Adaptar el lector de PixelPlayer quitando lo que no se usa |
| TagLib por JNI propio | Auxio `musikr/src/main/cpp/` (`taglib_jni.cpp`, `JMetadataBuilder.cpp`), con TagLib como submódulo | **Plan B.** Su build compila TagLib con scripts de shell (`pkg/${ANDROID_ABI}`): es lo que no anda en Windows (riesgo de la spec, sección 11) |
| Interpretar etiquetas | Auxio `musikr/tag/` (`interpret/`, `parse/`, `Date.kt`, `Disc.kt`, `Name.kt`) | Adaptar: géneros con varios valores, fechas parciales, `albumartist` frente a `artist`, ordenación sin artículos |
| Pipeline de indexación | Auxio `musikr/pipeline/` (Explore → Extract → Evaluate) y `musikr/cache/` | **Ideas** para el escaneo incremental por `(ruta, tamaño, mtime)`. Spindle recorre `File` directo (ADR-001) en vez de MediaStore o SAF |
| Profundidad de bits | Gramophone `logic/utils/AudioFormatDetector.kt` | **Pendiente:** confirmar en F1 si el AAR de Kyant0 la entrega. Si no, leer el encabezado (FLAC STREAMINFO, WAV fmt) o tomar la detección de Gramophone. Va con un ADR |
| Portadas | Auxio `musikr/covers/` (`fs/`, `embedded/`, `chained/`) | Ideas para "`cover.jpg` primero y después la incrustada". La caché de miniaturas se escribe en F1 |

### `playback`: servicio, cola y reglas (F2)

| Necesidad | Referencia | Qué tomar |
|---|---|---|
| `MediaSessionService` + ExoPlayer | Gramophone `logic/GramophonePlaybackService.kt` (1.994 líneas) | Adaptar **solo el esqueleto**: el servicio en primer plano, `MediaSession`, foco de audio y `setHandleAudioBecomingNoisy`. El resto (letras en la notificación, ajustes de Meizu) no va |
| Modelo de cola | Auxio `playback/queue/`, `playback/state/` y `playback/persist/` | Ideas para separar el contexto de la cola, y para guardar y restaurar el estado (retomar en pausa). Las reglas de Spindle (parada definitiva, aleatorio dentro del contexto, repetir que se apaga al cambiar de contexto) se escriben de cero, con TDD |
| Sin cortes entre pistas | Auxio `playback/` (su Media3 modificado) | Ideas. Media3 1.11 ya trae gapless para MP3/AAC/FLAC/Opus; se verifica en el teléfono |
| ReplayGain (F6) | Gramophone `logic/utils/ReplayGainAudioProcessor.kt` + `ReplayGainUtil.kt`; Auxio `playback/replaygain/` | Adaptar el `AudioProcessor` de Gramophone (ReplayGain 2.0) |

### `userdata`: listas y datos (F4)

| Necesidad | Referencia | Qué tomar |
|---|---|---|
| Lectura y escritura de M3U8 | Auxio `musikr/playlist/m3u/M3U.kt` (297 líneas) | Adaptar: rutas relativas a `../Biblioteca/` y reconciliar rutas |
| M3U con rutas de archivo | Symphony | **Solo ideas** (AGPL) |
| Stats, recortes y exportar/importar | — | Se escribe de cero (`kotlinx.serialization`, escritura atómica, mezcla de 4.3) |

### Letras (F6, opcional)

| Necesidad | Referencia | Qué tomar |
|---|---|---|
| LRC | Gramophone `logic/utils/LrcUtils.kt` (256 líneas) + `SemanticLyrics.kt` | Adaptar solo el lector LRC básico |

### UI (todas las fases)

| Necesidad | Referencia | Qué tomar |
|---|---|---|
| Unir Compose, Media3 (`MediaController`) y Room | PixelPlayer (`data/`, `presentation/`) | Ideas de cableado. Su UI Material y Hilt no van: Spindle usa `ui/tui` propio y DI manual |

### Station (F3)

| Necesidad | Referencia | Qué tomar |
|---|---|---|
| Bajar e importar | beets-ytimport (`beetsplug/ytimport/youtube.py`, `__init__.py`) | **Se instala como plugin.** Ya guarda `yt_id`, `yt_source` y la URL en `comment`, prefiere Opus/M4A sin recodificar y tiene modo silencioso con respaldo "as-is" |
| SponsorBlock `music_offtopic` | yt-dlp (`SponsorBlock` + `ModifyChapters` como postprocesadores) | beets-ytimport **no lo trae**: hay que agregarlo a sus opciones de yt-dlp (spec 6.3). Ver si se puede configurar o si hace falta un parche chico |
| Recortar la miniatura a cuadrado | yt-dlp (`--convert-thumbnails` + `--ppa "ThumbnailsConvertor+ffmpeg_o:-vf crop=ih:ih"`) | Mismo punto que el anterior |

## Decisiones que salen de esto (se registran como ADR al llegar a cada fase)

1. **F1:** TagLib mediante el AAR de Kyant0 (recomendado), con el JNI de Auxio como plan B.
2. **F1:** de dónde sale la profundidad de bits.
3. **F3:** cómo meter SponsorBlock y el recorte de la miniatura en beets-ytimport.
