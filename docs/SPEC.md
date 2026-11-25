# Spindle — Reproductor de música local, sin algoritmos

> Especificación para construir con Claude Code · v0.2 · 7 nov 2026
> Proyecto hermano: **Bare** (launcher minimalista, documento aparte). Son apps independientes: comparten lenguaje visual y se integran solo mediante APIs estándar de Android.

---

## 0. Instrucciones para Claude Code

- Este documento es la fuente de verdad. Léelo completo antes de escribir código y cópialo al repo como `docs/SPEC.md`.
- El repo tiene dos componentes:
  - `android/`: la app Spindle.
  - `station/`: **Spindle Station**, la herramienta de PC que descarga, organiza y prepara la música (comando `station`).
- Crea `CLAUDE.md` en la raíz con: resumen, comandos de build e instalación de ambos componentes, convenciones y estructura.
- Trabaja por fases (sección 10), en orden. Una fase termina cuando todos sus criterios de aceptación se cumplen **en el teléfono real y en el PC del usuario**.
- Registra cada decisión técnica no trivial en `docs/DECISIONES.md` (formato ADR corto: contexto · decisión · alternativas · consecuencias).
- Si algo de esta spec resulta inviable, documenta lo encontrado y propón una alternativa al usuario antes de seguir.
- Las decisiones abiertas (sección 12) tienen un valor por defecto. Úsalo, pero confírmalo con el usuario al llegar a esa parte.
- **No reinventar la rueda.** La sección 14 lista proyectos open source de los que hay que partir.
  - En la Fase 0, clónalos en `refs/` (que va en `.gitignore`).
  - Para cada módulo, revisa primero cómo lo resolvieron ellos y reutiliza su código cuando la licencia lo permita, adaptándolo y recortándolo.
  - Registra cada archivo o fragmento reutilizado en `THIRD_PARTY_NOTICES.md`.
- Licencia de este proyecto: **GPL-3.0** (ver 14.4).
- Idiomas: interfaz y mensajes de la CLI en español (Chile); código, identificadores, commits y comentarios en inglés.
- Entorno del usuario:
  - PC con Windows: desarrollo de la app (Android Studio / Gradle + ADB por USB) y ejecución de Station de forma nativa (no requiere WSL ni Docker).
  - Notebook con Linux: Station también debe funcionar ahí.
  - Teléfono: Xiaomi Redmi 10 5G (ver `docs/DISPOSITIVO.md`).

---

## 1. Visión

Una biblioteca musical propia, guardada en el teléfono, que se usa con la comodidad de Spotify (encontrar rápido, encolar, listas, aleatorio, controles en la pantalla de bloqueo, retomar donde quedaste), pero sin nada que decida por el usuario: sin recomendaciones, sin reproducción automática de lo que no eligió y sin telemetría. Se navega como un reproductor MP3/iPod clásico, con estética de terminal.

La música se consigue intencionalmente (sobre todo desde YouTube, donde hay mucho que no está en Spotify), se ordena en el PC y llega sola al teléfono.

**La app funciona 100 % sin conexión y sin el PC.** El PC solo hace falta para incorporar música nueva y para respaldar los datos del usuario. Es el mismo patrón que el usuario ya usa con su app Alba: el PC trabaja de madrugada, el teléfono tiene el resultado todo el día.

---

## 2. Principios de diseño

1. **Tú eliges todo lo que suena.** Cuando termina lo que elegiste (álbum, lista o cola), silencio, salvo que tú mismo hayas activado "repetir". No hay radio, autocompletado ni "porque escuchaste…".
2. **Comodidad sí, manipulación no.** Se toma de Spotify lo que facilita: cola, listas, búsqueda, aleatorio. Se excluye lo que decide por ti o te retiene: recomendaciones, "recientes" o "más escuchados" en portada, sugerencias.
3. **Los archivos son la verdad.** La base de datos del teléfono es un índice que se puede reconstruir. Los datos del usuario (listas, conteos, recortes) se exportan a formatos abiertos (JSON, M3U8) y se respaldan en el PC.
4. **Independiente del PC y de cualquier nube.**
5. **Sin pérdida innecesaria.** Nunca recodificar audio con pérdida: lo que viene en Opus se queda en Opus.
6. **Lenguaje visual compartido con Bare:** negro, texto, monoespaciado.

---

## 3. Componentes y flujo

```
  fuentes ── YouTube / YouTube Music · Bandcamp · CDs propios
     │
     ▼
  SPINDLE STATION (PC)     yt-dlp → beets → (ffmpeg)
     │  escribe en
     ▼
  Biblioteca/  ── Syncthing (PC envía → teléfono recibe) ──►  SPINDLE (teléfono)
                                                                 │
  Datos/  ◄────────── Syncthing (ambos sentidos) ──────────────► │
  (pedidos, listas, estadísticas, recortes)                      │
                                                                 ▼
                              "♪ pedir a Spindle" desde la micro-app de YouTube de Bare,
                              o compartir un enlace desde WhatsApp → pedidos.txt
```

- **A. Spindle (Android):** indexa, navega, reproduce, guarda datos del usuario y recibe pedidos.
- **B. Spindle Station (PC):** herramienta de línea de comandos en Python que descarga, etiqueta, organiza y procesa pedidos.
- **C. Syncthing:** sincroniza por Wi-Fi local dos carpetas entre el PC y el teléfono.

---

## 4. Carpetas y formatos de datos

### 4.1 En el teléfono

| Ruta | Tipo Syncthing en el teléfono | Quién escribe |
|---|---|---|
| `Music/Spindle/Biblioteca/` | **Solo recibir** (*Receive Only*) | Solo Station. Spindle **nunca** escribe aquí |
| `Music/Spindle/Datos/` | **Enviar y recibir** | Spindle y Station |

### 4.2 Estructura de la biblioteca (la genera beets)

```
Biblioteca/
  Radiohead/
    2007 - In Rainbows/
      01 15 Step.flac
      …
      cover.jpg
      notes.md            ← opcional: libreto personal, escrito en el PC
  Varios artistas/
    2001 - Amélie (BSO)/…
  Mon Laferte/
    Sencillos/
      Tu Falta de Querer.opus
```

- Agrupar por **artista del álbum** (`albumartist`), no por artista de la pista.
- Las recopilaciones usan `albumartist = "Varios artistas"`.
- Las canciones sueltas (típicas de YouTube) usan `album = "Sencillos"` con el artista correspondiente, salvo que traigan un álbum real en sus metadatos (YouTube Music suele traerlo).

### 4.3 Datos del usuario (`Datos/`)

```
Datos/
  pedidos.txt              ← pedidos desde el teléfono (una línea por pedido)
  pedidos-hechos.txt       ← Station anota el resultado de cada pedido
  listas/
    para estudiar.m3u8     ← rutas relativas a ../Biblioteca/
  estadisticas.json        ← conteo y última reproducción por pista
  recortes.json            ← inicio/fin no destructivos por pista
  archivo-descargas.txt    ← registro de yt-dlp para no descargar dos veces
```

- **Clave estable de pista** para estadísticas y recortes:
  1. ID de MusicBrainz (`mb_trackid`) si existe.
  2. Si no, el ID de YouTube guardado en las etiquetas por Station.
  3. Si no, la ruta relativa.
- Si beets renombra un archivo, Spindle debe reconciliar por clave. Documentar la estrategia en un ADR.
- Formatos de ejemplo:

```json
// estadisticas.json
{ "version": 1, "pistas": { "mb:6f2a…": { "veces": 12, "ultima": "2026-11-06T22:41:00-03:00" } } }

// recortes.json
{ "version": 1, "pistas": { "yt:dQw4w9WgXcQ": { "inicio_ms": 12000, "fin_ms": 213500 } } }
```

- Escritura atómica (archivo temporal + renombrar).
- Al importar, mezclar: `veces = máximo`, `ultima = más reciente`.
- Ignorar los archivos `*.sync-conflict-*` que crea Syncthing, pero registrarlos en `info`.

---

## 5. App Android — Spindle

### 5.1 Lenguaje visual

Igual que Bare:

- Fondo `#000000`, texto `#FFFFFF`, secundario `#8A8A8A`. Acento único ámbar `#FFB000` solo para lo que está sonando.
- Tipografía monoespaciada incluida (JetBrains Mono, IBM Plex Mono o Hack). Sin íconos: los controles son palabras.
- Escala: título 24 sp · menú raíz 20 sp · listas 16 sp · secundario 14 sp. A 20 sp las filas de las listas no caben en el ancho del teléfono (unas 28 columnas).
- Áreas táctiles ≥ 48 dp.
- Sin portadas grandes. Portada pequeña opcional en "sonando" (E5).

### 5.2 Navegación (estilo iPod)

**Menú raíz**

```
  spindle                       ▶ nude 2:31/4:15

  › artistas
  › álbumes
  › canciones
  › géneros
  › listas
  › cola (3)
  › sacar un disco al azar

  > _                        escribe para buscar
```

**Recorridos**

- artistas › álbumes del artista (por año) › pistas
- álbumes › pistas (alfabético por título; opción de ordenar por artista o año)
- canciones › todas las pistas (alfabético)
- géneros › artistas del género › álbumes › pistas. Un álbum con varios géneros aparece en todos ellos.
- listas › pistas de la lista
- "Varios artistas" aparece como un artista más.
- Música clásica: se agrupa por artista del álbum; si existe la etiqueta de compositor, se muestra en la vista de pista.

**Reglas de las listas**

- En cualquier lista, **escribir filtra** (la misma mecánica que Bare). Desde el menú raíz, escribir busca en artistas, álbumes y canciones a la vez, con resultados agrupados.
- Arriba, siempre la ruta de navegación: `spindle › artistas › radiohead › in rainbows`.
- Sin secciones de "recientes", "más escuchados" ni "para ti".

### 5.3 Vista de álbum

```
  spindle › radiohead › in rainbows (2007)
  [FLAC 16-bit/44.1kHz] · 10 pistas · 42:39 · rock

   1  15 step                          3:57
   2  bodysnatchers                    4:02
  ▶3  nude                             4:15
   4  weird fishes/arpeggi             5:18
   …

  [reproducir] [aleatorio] [+ cola] [notas]
```

- El indicador técnico se muestra a nivel de álbum (y por pista si varía): `[FLAC 24-bit/96kHz]`, `[OPUS 160k]`, `[MP3 320k]`, `[AAC 128k]`.
- `notas` aparece solo si existe `notes.md` en la carpeta del álbum.
- Las pistas con recorte muestran `✂` junto a la duración.
- Mantener presionada una pista: `reproducir a continuación` · `agregar al final de la cola` · `agregar a lista…` · `recortar` · `info`.

### 5.4 Sonando

```
  ▶ nude
    radiohead — in rainbows · 3/10
    [██████████░░░░░░░░░░] 2:31 / 4:15
    [FLAC 16-bit/44.1kHz]

    [ ant ]   [ pausa ]   [ sig ]

    aleatorio: sí · repetir: no
    después: 7 pistas del álbum · cola 2

    [recortar] [+ lista] [info]
```

- La barra se puede arrastrar para adelantar o retroceder.
- `después:` dice exactamente qué sonará y cuándo se detendrá: no hay sorpresas.

### 5.5 Reglas de reproducción

- **Contexto:** tocar una pista reproduce su contexto (álbum, lista, o lista filtrada) desde esa pista.
- **Cola:** `reproducir a continuación` y `agregar al final`. La cola es visible y editable (reordenar, quitar, vaciar). Orden: primero la cola manual, después lo que queda del contexto.
- **Aleatorio (como Spotify):** botón que mezcla el contexto actual (álbum, lista, artista o "todas las canciones"). Permanece activado al cambiar de contexto hasta que el usuario lo apague. Solo mezcla lo que el usuario eligió: nunca agrega pistas de fuera del contexto.
- **Repetir (como Spotify):** un botón que rota entre `no` → `todo` → `una pista`.
  - `todo` vuelve a empezar el contexto actual (álbum o lista) al terminar.
  - `una pista` repite la canción actual.
  - Se apaga al cambiar de contexto, para que un álbum en bucle no quede sonando sin querer la próxima vez.
- **Parada definitiva:** con repetir en `no`, cuando terminan la cola y el contexto, la reproducción se detiene y la sesión queda en pausa al final. Nunca se reproduce nada que el usuario no haya puesto en marcha: ni siquiera "repetir todo" agrega música de fuera del contexto elegido.
- **Sacar un disco al azar:** elige un álbum al azar (distribución uniforme) de toda la biblioteca y muestra su ficha con `[poner]` o `[devolver]`. Tras `devolver`, la opción queda deshabilitada hasta que el usuario reproduzca algo elegido a mano (E4), igual que con una estantería real.
- **Retomar:** al cerrar se guarda contexto, pista, posición, cola y estado de aleatorio. Al abrir se restaura **en pausa**, sin empezar a sonar.
- **Sin cortes entre pistas** (gapless) cuando el formato lo permite.
- **Foco de audio:**
  - Pausa ante llamadas y alarmas; baja el volumen con notificaciones.
  - Pausa al desconectar los audífonos (`setHandleAudioBecomingNoisy(true)`).
- **Controles externos:** en la pantalla de bloqueo, la notificación y los botones Bluetooth, mediante `MediaSession`. Publicar metadatos completos para que Bare muestre `▶ artista — título`.
- **Conteo de reproducciones:** cuenta cuando se escuchó más del 50 % de la pista o más de 4 minutos.
- **Temporizador para dormir** (Fase 6): `al terminar esta pista` · `al terminar el álbum` · `en N minutos`.
- **Normalización de volumen** (Fase 6, E8): leer etiquetas ReplayGain (las escribe beets) y aplicar la atenuación correspondiente por pista o por álbum. Partir de Auxio o Gramophone (14.1).

### 5.6 Recorte no destructivo

- Desde "sonando" › `recortar`: marcar inicio y fin en la posición actual, o escribirlos como `m:ss`.
- Se guarda en `recortes.json` y se aplica con `MediaItem.ClippingConfiguration` de Media3. El archivo original no se toca.
- `quitar recorte` vuelve a la pista completa.
- Station puede aplicar estos recortes al archivo de forma definitiva (sección 6), si el usuario lo pide.

### 5.7 Libreto (`notes.md`)

- Si la carpeta del álbum tiene `notes.md`, se muestra con un renderizador de Markdown liviano: títulos, listas, negrita, cursiva y enlaces como texto.
- Usos previstos: reseña personal, cuándo y cómo se descubrió el disco, letras.
- En v1 es **solo lectura** en el teléfono; se escribe en el PC (E6).
- Letras (Fase 6, opcional): mostrar la etiqueta de letra incrustada o un `<pista>.lrc` en `info`. Gramophone tiene un buen lector de LRC para tomar como base.

### 5.8 Pedidos (puente con Station)

- Spindle se registra como destino de **Compartir** para texto y enlaces (`ACTION_SEND`, `text/plain`). Fuentes:
  - la acción `♪ pedir a Spindle` en la micro-app de YouTube de Bare (el camino principal);
  - compartir un enlace desde WhatsApp u otra app;
  - las notas de Bare con prefijo `♪`.
- Cada pedido agrega una línea a `Datos/pedidos.txt`: `2026-11-07 02:22 | https://youtu.be/…`. Confirma con `pedido guardado` y se cierra.
- Pedido manual desde ajustes: texto libre `artista - canción`; Station lo busca.
- Ajustes › pedidos muestra los pendientes y, leyendo `pedidos-hechos.txt`, el resultado de los procesados (`listo`, `no encontrado`, `requiere revisión`).

### 5.9 Indexación

- **Acceso a archivos:** "Acceso a todos los archivos" (`MANAGE_EXTERNAL_STORAGE`). Es una app personal y lo necesita para:
  - recorrer la biblioteca rápido con la API de archivos;
  - leer `notes.md` y `cover.jpg`, que MediaStore no expone;
  - escribir en `Datos/`.

  No usar MediaStore como fuente: además de no ver archivos que no son audio, no entrega profundidad de bits ni frecuencia de muestreo. Registrar la decisión y la alternativa (Storage Access Framework) en un ADR.
- **Extensiones:** `flac`, `mp3`, `opus`, `ogg`, `m4a` (AAC y ALAC), `wav`.
- **Escaneo incremental** por `(ruta, tamaño, fecha de modificación)`. Se dispara:
  - al abrir la app (en segundo plano);
  - al volver a la app si pasaron más de 10 minutos;
  - con `reindexar` en ajustes.
- **Etiquetas a leer:** título, artista, artista del álbum, álbum, número de pista y disco, año o fecha, género (multivalor; separar solo por `;`, porque `/` aparece dentro de géneros reales como "R&B/Soul"), compositor, IDs de MusicBrainz, ID de YouTube, ReplayGain, portada incrustada y letra. La portada y la letra no se leen en F1: no hay dónde mostrarlas hasta E5 y F6.
- **Datos técnicos:** códec, frecuencia de muestreo, profundidad de bits, tasa de bits, canales y duración.
- **Lector de etiquetas: TagLib.** Lo usan Auxio y PixelPlayer (14.1). Claude Code elige la forma de integrarlo (la de Auxio, la de PixelPlayer u otro binding de TagLib para Android) y lo documenta en un ADR. Requisito: soportar los seis formatos anteriores.
- **Rendimiento:** primera indexación de 5.000 pistas en menos de 60 s en el dispositivo; incremental sin cambios en menos de 3 s.
- **Portadas:** preferir `cover.jpg` de la carpeta; si no existe, la incrustada. Miniaturas en caché.

### 5.10 Datos del usuario

- Room guarda listas, estadísticas, recortes y estado de reproducción.
- **Exportar:** cada cambio se exporta a `Datos/` (con un pequeño retardo para agrupar cambios).
- **Importar:** si la base está vacía (reinstalación) o los archivos de `Datos/` son más nuevos, aplicando la mezcla de 4.3.
- **Listas:** se guardan como `.m3u8` con rutas relativas, legibles por cualquier otro reproductor.

---

## 6. Spindle Station (PC)

Herramienta de línea de comandos en Python que convierte enlaces y pedidos en música bien etiquetada dentro de `Biblioteca/`. **Se construye sobre beets-ytimport** (14.2), un plugin de beets que ya combina yt-dlp, ytmusicapi y beets y evita recodificar. No hay que escribir ese pegamento desde cero.

### 6.1 Entorno

- Python 3.12 o superior, instalada como herramienta (`pipx` o `uv tool`).
- Dependencias: `beets` con `beets-ytimport` y otros plugins, `yt-dlp`, `ytmusicapi` y el binario `ffmpeg`.
- **Windows 10/11 nativo es la plataforma principal** (ffmpeg con `winget`). Debe funcionar igual en Linux. No requiere WSL ni Docker.
- Configuración en `station.toml` (rutas de `staging/`, `Biblioteca/` y `Datos/`; opciones de descarga) más la `config.yaml` de beets que genera Station.
- Antes de cada lote, actualizar yt-dlp: YouTube cambia a menudo y una versión vieja falla.

### 6.2 Comandos

| Comando | Qué hace |
|---|---|
| `station bajar <url \| "artista - canción">` | Descarga e importa. Acepta videos, listas y álbumes de YouTube Music. Para búsquedas de texto, usar la búsqueda de YouTube Music (ytmusicapi), mostrar los 3 mejores resultados y preferir canciones oficiales con álbum (E7) |
| `station importar [carpeta]` | Importa `staging/` (o una carpeta de Bandcamp o de un CD ripeado) a `Biblioteca/` con beets |
| `station pedidos` | Procesa `Datos/pedidos.txt` (bajar + importar) y anota el resultado en `pedidos-hechos.txt` |
| `station recortar <archivo> --inicio m:ss --fin m:ss` | Recorte definitivo con ffmpeg **sin recodificar** (`-c copy`), con respaldo del original |
| `station aplicar-recortes` | Aplica a los archivos los recortes de `recortes.json` que el usuario marque como definitivos |
| `station notas <álbum>` | Crea un `notes.md` de plantilla (fecha de adquisición, fuente, reseña) |
| `station revisar` | Informe de pistas sin género, año o portada, posibles duplicados y conflictos de Syncthing |
| `station estado` | Resumen: pistas, álbumes, espacio, pedidos pendientes, última ejecución |

### 6.3 Descarga (requisitos sobre lo que ya trae beets-ytimport)

- **Solo audio, sin recodificar:** extraer el mejor audio disponible tal cual (normalmente Opus o AAC). No convertir a MP3 ni FLAC: no mejora la calidad y ocupa más.
- **Metadatos y portada:** incrustarlos. Recortar la miniatura de YouTube (16:9) a cuadrado antes de incrustarla.
- **Quitar lo que no es música:** eliminar automáticamente con SponsorBlock la categoría `music_offtopic` (introducciones habladas, escenas y otras partes que no son la canción en videos musicales). No usar las categorías `intro` u `outro` en música. Si beets-ytimport no lo expone, agregarlo a las opciones de yt-dlp que usa.
- **Guardar el ID de YouTube** en una etiqueta, para la clave estable (4.3).
- **Sin duplicados:** usar `--download-archive` con `Datos/archivo-descargas.txt`.
- **Álbumes de YouTube Music:** conservar el número de pista y el nombre del álbum.
- **Si no hay metadatos musicales:** intentar `Artista - Título` a partir del título del video y marcar la pista para revisión.

### 6.4 Organización con beets (requisitos)

- Plantillas de rutas según 4.2: álbumes, sencillos y recopilaciones.
- Autoetiquetado con MusicBrainz cuando hay coincidencia. Si no la hay (frecuente con YouTube), importar conservando los metadatos de yt-dlp (en beets-ytimport: modo silencioso con respaldo "as-is") y anotarlo para `revisar`.
- Plugins:
  - obligatorios: `fetchart` y `embedart` (portadas), y uno de géneros (`lastgenre` o los de MusicBrainz; documentar la elección);
  - opcionales: `replaygain` (E8) y `scrub`.
- Nunca borrar los archivos de origen hasta que la importación se haya completado bien.

### 6.5 Automatización (Fase 5)

- Una tarea del Programador de tareas de Windows ejecuta `station pedidos` en la madrugada, aprovechando que el PC ya se enciende a esa hora para Alba.
- Escribe un log en `station/logs/`. Syncthing lleva el resultado al teléfono.

### 6.6 Nota sobre fuentes

Las condiciones de servicio de YouTube no permiten descargar contenido fuera de sus funciones oficiales, y la legalidad de copiar música depende del país. Es una herramienta de uso personal. Cuando un disco esté a la venta en formato descargable (Bandcamp, Qobuz) o se tenga en CD, preferir esa fuente: además suena mejor. `station importar` acepta esas carpetas directamente.

---

## 7. Syncthing

- **PC (Windows):** Syncthing con su aplicación de bandeja.
- **Teléfono:** Syncthing-Fork. La app oficial de Syncthing para Android fue discontinuada; el fork lo mantienen nuevos mantenedores.
- **Carpetas:**

| Carpeta | PC | Teléfono |
|---|---|---|
| Biblioteca | Solo enviar | Solo recibir |
| Datos | Enviar y recibir | Enviar y recibir |

- En el teléfono: sincronizar solo con Wi-Fi; inicio automático y sin restricciones de batería en MIUI.
- Plan B si Syncthing falla: `adb push` o copia por USB de `Biblioteca/`. Documentarlo en `CLAUDE.md`.

---

## 8. Arquitectura técnica (Android)

- **Lenguaje y UI:** Kotlin + Jetpack Compose. Material 3 solo como base técnica; componentes visibles propios (`ui/tui`), alineados con Bare.
- **SDK:** `minSdk 30`. `targetSdk` y `compileSdk`: la última versión estable al momento de compilar.
- **Reproducción:** Media3 (ExoPlayer + `MediaSessionService`) como servicio en primer plano de tipo `mediaPlayback`. La interfaz se conecta con `MediaController`.
- **Modelo de reproducción propio:** contexto + cola manual + modos, traducido a la lista de ExoPlayer. `REPEAT_MODE_ALL` solo se usa cuando el usuario activa "repetir todo", y abarca únicamente el contexto elegido. Con repetir en `no`, al llegar a `STATE_ENDED`, detener.
- **Persistencia:** Room; `kotlinx.serialization` para JSON. Coroutines + Flow. Inyección de dependencias manual.
- **Paquetes:** `library` (escáner, etiquetas, repositorio), `playback` (servicio, cola, reglas), `userdata` (listas, estadísticas, recortes, exportar/importar), `share` (pedidos), `ui/screens`, `ui/tui`.
- **Pruebas unitarias obligatorias:**
  - reglas de cola y parada definitiva;
  - aleatorio limitado al contexto;
  - conteo de reproducciones;
  - mezcla de importación;
  - lectura y escritura de M3U8;
  - lectura de etiquetas con archivos de prueba pequeños de cada formato, generados con ffmpeg en el setup de pruebas (no subir audio con derechos al repo).
- **Station:** pruebas con `pytest` para el análisis de pedidos, plantillas de rutas y lectura de configuración. Las descargas reales solo en pruebas manuales.

---

## 9. Dispositivo

- Mismas notas de Xiaomi que en Bare:
  - Identificar el modelo por ADB y registrarlo en `docs/DISPOSITIVO.md`.
  - Activar "Instalar vía USB" y "Depuración USB (ajustes de seguridad)".
  - Inicio automático activado, batería "Sin restricciones" y candado en recientes. Sin esto, MIUI puede cortar la música con la pantalla apagada.
- Conceder el acceso a archivos por ADB: `adb shell appops set <pkg> MANAGE_EXTERNAL_STORAGE allow`.
- Claude Code escribe `scripts/setup-device.ps1` con estos pasos y la lista de ajustes manuales.

---

## 10. Fases y criterios de cierre

| Fase | Contenido | Se cierra cuando… |
|---|---|---|
| F0 Esqueleto | Repo con `android/` y `station/`, `CLAUDE.md`, licencia GPL-3.0, build, instalación por ADB, `docs/DISPOSITIVO.md`. Clonar las referencias de la sección 14 en `refs/` y escribir `docs/referencias.md`. Biblioteca de prueba generada con ffmpeg (varios formatos, géneros, una recopilación, un álbum con `notes.md`) | La app se instala, obtiene acceso a archivos y lista las carpetas de la biblioteca de prueba; el usuario aprobó el plan de reutilización |
| F1 Biblioteca | Indexación incremental con TagLib, navegación estilo iPod completa, filtro al escribir, vista de álbum con indicador técnico | Navegar la biblioteca de prueba por los 4 recorridos (artistas, álbumes, canciones, géneros; `listas` llega con las M3U8 en F4); cumplir las metas de rendimiento |
| F2 Reproducción | Servicio Media3, contexto, cola, aleatorio, repetir (todo / una pista), parada definitiva, retomar en pausa, controles de bloqueo y Bluetooth, foco de audio, sin cortes entre pistas, sacar un disco al azar | Con la pantalla apagada, un álbum suena completo y se detiene; los audífonos pausan; Bare muestra la línea `▶` |
| F3 Station v1 | beets-ytimport configurado, `bajar`, `importar`, `revisar`, `estado`; guía y configuración de Syncthing | De punta a punta: un enlace de YouTube termina etiquetado, con portada cuadrada, sin la parte hablada, y suena en el teléfono |
| F4 Datos de usuario | Listas M3U8 (y el recorrido `listas`), estadísticas, recortes no destructivos, exportar/importar, `notes.md` | Reinstalar la app conserva listas, conteos y recortes vía `Datos/` |
| F5 Pedidos | Destino de Compartir, `station pedidos`, `pedidos-hechos.txt`, tarea programada de madrugada | `♪ pedir a Spindle` en la noche → la canción está en el teléfono a la mañana siguiente |
| F6 Pulido | Temporizador para dormir, ReplayGain, letras, `recortar`/`aplicar-recortes` definitivos, revisión de batería | Revisión final con el usuario |

---

## 11. Riesgos y limitaciones conocidas

- **YouTube cambia a menudo** y yt-dlp puede fallar hasta actualizarse. Mitigación: actualizar antes de cada lote; registrar los errores en `pedidos-hechos.txt` sin detener el lote.
- **Los metadatos de YouTube son desordenados.** Mitigación: preferir resultados de YouTube Music con álbum; `station revisar`; plantillas para sencillos.
- **SponsorBlock depende de la comunidad:** un video sin segmentos marcados se descarga completo. El recorte no destructivo en el teléfono cubre esos casos.
- **MIUI puede detener la reproducción** en segundo plano (ver sección 9).
- **El mantenimiento de Syncthing en Android ha sido inestable.** Plan B en la sección 7.
- **Calidad:** el audio de YouTube es con pérdida. El indicador técnico lo deja a la vista; no es un defecto que corregir.
- **Auxio no compila en Windows** (su Media3 modificado usa scripts de shell). Sirve para leer y copiar código, no como proyecto a compilar en el PC del usuario.

---

## 12. Decisiones abiertas (valor por defecto entre corchetes)

| # | Decisión | Estado / por defecto |
|---|---|---|
| E1 | Nombre | **Resuelta: Spindle** (PC: Spindle Station) |
| E2 | Aleatorio | **Resuelta:** permitido como en Spotify, dentro de lo que el usuario eligió |
| E3 | Repetir | **Resuelta:** `no` / `todo` / `una pista`, como en Spotify, limitado al contexto elegido |
| E4 | Cajón aleatorio: tras devolver un disco | [Deshabilitado hasta reproducir algo elegido a mano] |
| E5 | Portada pequeña en "sonando" | [Desactivada; activable en ajustes] |
| E6 | Escribir notas del álbum desde el teléfono | [No en v1; se escriben en el PC] |
| E7 | Pedidos de texto (sin enlace): ¿Station elige sola el mejor resultado o pregunta? | [En modo interactivo pregunta; en el lote de madrugada elige la canción oficial con álbum si existe y, si no, lo deja como "requiere revisión"] |
| E8 | Normalización de volumen (ReplayGain) | [Sí, en Fase 6] |

---

## 13. Integración con Bare

- **Línea `▶` en el inicio de Bare:** vía `MediaSession` (no requiere código específico).
- **Pedidos:** desde la micro-app de YouTube de Bare (`♪ pedir a Spindle`) y desde las notas con prefijo `♪`. Spindle los recibe como un `ACTION_SEND` más.
- **Lenguaje visual compartido.** Ninguna app depende de la otra para funcionar.

---

## 14. Bases open source y cómo reutilizarlas

No se hace fork de un reproductor completo: todos los candidatos traen una interfaz Material grande que habría que tirar entera, y Spindle es sobre todo su interfaz de texto y sus reglas de reproducción. En cambio, **el motor (indexación, etiquetas, reproducción, ReplayGain, letras) se toma de proyectos maduros**, y Station se construye sobre un plugin existente. En la Fase 0, Claude Code clona estos repos en `refs/` (`git clone --depth 1`), anota el commit y escribe `docs/referencias.md`.

### 14.1 App Android

| Proyecto | Licencia | Stack | Qué tomar |
|---|---|---|---|
| [Auxio](https://github.com/OxygenCobalt/Auxio) | GPLv3 | Kotlin, Media3 (modificado), TagLib | **Indexación y etiquetas:** su capa de indexación (carpeta `musikr`) y la lectura de metadatos con TagLib; sistema que unifica artistas y artistas del álbum; ReplayGain completo; reproducción sin cortes. Filosofía "solo reproduce música", la más parecida a Spindle. No compila en Windows: solo para leer y copiar |
| [Gramophone](https://github.com/FoedusProgramme/Gramophone) | GPLv3 | Kotlin, Media3 | **Reproducción y letras:** servicio Media3, ReplayGain 2.0, letras sincronizadas (LRC, TTML, SRT), navegación por carpetas. Usa MediaStore (Spindle no) |
| [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS) | GPLv3 | Kotlin, **Compose**, Media3, Room, TagLib, Hilt | El stack más parecido al de Spindle: cómo se conectan Compose, Media3, Room y TagLib en una app actual. Ignorar sus funciones de streaming (Navidrome, Jellyfin) |
| [Symphony](https://github.com/zyrouge/symphony) | AGPL-3.0 | Kotlin, Compose | Ordenar y navegar por rutas de archivos, lector de M3U. **Solo ideas** (licencia AGPL, ver 14.4); sin versiones nuevas desde dic 2024 |
| [Syncthing-Fork](https://github.com/researchxxl/syncthing-android) | MPL-2.0 (verificar) | Android | Cliente de Syncthing para el teléfono (se instala, no se copia código) |

### 14.2 Spindle Station (PC)

| Proyecto | Licencia | Qué tomar |
|---|---|---|
| [beets-ytimport](https://github.com/mgoltzsche/beets-ytimport) | Apache-2.0 | **Base de Station:** plugin de beets que descarga con yt-dlp, usa ytmusicapi, prefiere Opus/M4A sin recodificar y tiene modo silencioso con respaldo "as-is". Station lo envuelve, lo configura y agrega pedidos, recortes y revisión |
| [beets](https://github.com/beetbox/beets) | MIT | Organizador y autoetiquetado (MusicBrainz), plugins de portadas, géneros y ReplayGain |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense | Descarga, SponsorBlock (`music_offtopic`), metadatos y miniaturas |
| [ytmusicapi](https://github.com/sigma67/ytmusicapi) | Verificar | Búsqueda en YouTube Music con metadatos de álbum, para los pedidos de texto (E7) |
| [beets-yt-dlp](https://pypi.org/project/beets-yt-dlp/) | Verificar | Alternativa a beets-ytimport con búsqueda (`--search`). Referencia |
| [ytbdl](https://pypi.org/project/ytbdl/) | Verificar | Otra combinación yt-dlp + beets. Referencia |

### 14.3 Cómo reutilizar

1. Leer el código de referencia del módulo antes de diseñarlo y resumir en `docs/referencias.md` cómo lo resuelve.
2. Copiar solo lo necesario, adaptarlo al estilo del proyecto (Kotlin, Compose, sin Hilt) y quitar lo que sobra.
3. Mantener el aviso de copyright original en cada archivo copiado y registrarlo en `THIRD_PARTY_NOTICES.md`.
4. No compilar los proyectos de `refs/` como parte del build de Spindle ni agregarlos como submódulos. Las dependencias de Station (beets, beets-ytimport, yt-dlp) sí se instalan como paquetes.

### 14.4 Licencias

- Spindle se publica como **GPL-3.0**. Así se puede incorporar código GPLv3 (Auxio, Gramophone, PixelPlayerOSS), MIT y Apache-2.0. Las obligaciones de la GPL solo aplican si algún día se distribuye la app.
- **Solo ideas, sin copiar código:**
  - AGPL-3.0 (Symphony), para no arrastrar sus condiciones adicionales;
  - proyectos sin licencia;
  - proyectos cuya licencia no se haya verificado.
- Si una licencia no está clara, Claude Code la verifica en el repo antes de copiar.
