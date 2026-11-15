#!/usr/bin/env python3
"""Generate a small synthetic music library with ffmpeg (no copyrighted audio).

Covers every format Spindle reads, multi-value genres, a compilation, a single,
a folder cover, an embedded cover, notes.md and a high-res file.

    python scripts/make_testlib.py [out_dir]     # default: testlib/
"""
import json
import shutil
import subprocess
import sys
import zlib
from pathlib import Path

# (codec args, extension, expected ffprobe codec, tags supported)
FORMATS = {
    "flac": (["-c:a", "flac", "-sample_fmt", "s16", "-ar", "44100"], "flac", "flac", True),
    "mp3": (["-c:a", "libmp3lame", "-b:a", "320k", "-id3v2_version", "4"], "mp3", "mp3", True),
    "opus": (["-c:a", "libopus", "-b:a", "160k"], "opus", "opus", True),
    "vorbis": (["-c:a", "libvorbis", "-q:a", "5"], "ogg", "vorbis", True),
    "aac": (["-c:a", "aac", "-b:a", "128k"], "m4a", "aac", True),
    "alac": (["-c:a", "alac", "-sample_fmt", "s16p", "-ar", "44100"], "m4a", "alac", True),
    "wav": (["-c:a", "pcm_s24le", "-ar", "96000"], "wav", "pcm_s24le", False),
}

# folder, album tags, [(format, title, track artist)], extras
ALBUMS = [
    ("Ensayo Uno/2019 - Señales",
     {"album_artist": "Ensayo Uno", "album": "Señales", "date": "2019", "genre": "rock"},
     [("flac", "Primera Señal", None), ("flac", "Estática", None), ("flac", "Última Luz", None)],
     {"cover": True, "notes": True}),
    ("Ensayo Uno/2021 - Ruido Blanco",
     {"album_artist": "Ensayo Uno", "album": "Ruido Blanco", "date": "2021", "genre": "rock;electrónica"},
     [("mp3", "Frecuencia", None), ("mp3", "Interferencia", None)],
     {"embedded_cover": True}),
    ("Ensayo Uno/Sencillos",
     {"album_artist": "Ensayo Uno", "album": "Sencillos", "date": "2024", "genre": "pop",
      "yt_id": "aaaaaaaaaaa", "yt_source": "youtube",
      "comment": "https://www.youtube.com/watch?v=aaaaaaaaaaa Ensayo Uno - Tono Suelto"},
     [("opus", "Tono Suelto", None)], {}),
    ("Coro Austral/2018 - Hielo",
     {"album_artist": "Coro Austral", "album": "Hielo", "date": "2018", "genre": "ambient;clásica",
      "composer": "Compositora Ficticia"},
     [("opus", "Glaciar", None), ("opus", "Deshielo", None)], {"cover": True}),
    ("Coro Austral/2020 - Alta Resolución",
     {"album_artist": "Coro Austral", "album": "Alta Resolución", "date": "2020", "genre": "clásica"},
     [("wav", "Silencio Largo", None)], {}),
    ("Varios artistas/2001 - Recopilación de Prueba",
     {"album_artist": "Varios artistas", "album": "Recopilación de Prueba", "date": "2001",
      "genre": "bso", "compilation": "1"},
     [("aac", "Tema de Apertura", "Banda Uno"), ("alac", "Interludio", "Dúo Dos"),
      ("vorbis", "Créditos", "Trío Tres")], {"cover": True}),
]

NOTES = """# Señales

Adquirido: 2026-11-07 · Fuente: generado con ffmpeg

Disco de **prueba** para *Spindle*. Tonos sinusoidales, nada más.

- Pista 1: 440 Hz
- Pista 2: 550 Hz
"""


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-v", "error", "-y", *args], check=True)


def probe(path):
    out = subprocess.run(["ffprobe", "-v", "error", "-show_entries",
                          "stream=codec_name,codec_type:stream_tags:format_tags", "-of", "json", str(path)],
                         check=True, capture_output=True, text=True).stdout
    data = json.loads(out)
    tags = {k.lower(): v for k, v in data.get("format", {}).get("tags", {}).items()}
    for s in data["streams"]:
        tags |= {k.lower(): v for k, v in s.get("tags", {}).items()}
    audio = next(s["codec_name"] for s in data["streams"] if s["codec_type"] == "audio")
    return audio, tags


def make_cover(path, color):
    ffmpeg("-f", "lavfi", "-i", f"color=c={color}:s=300x300", "-frames:v", "1", str(path))


def main(out):
    lib = out / "Biblioteca"
    shutil.rmtree(lib, ignore_errors=True)
    freq, made = 440, []
    for folder, album_tags, tracks, extras in ALBUMS:
        d = lib / folder
        d.mkdir(parents=True)
        if extras.get("cover"):
            make_cover(d / "cover.jpg", "0x%06x" % (zlib.crc32(folder.encode()) & 0x7F7F7F))
        if extras.get("notes"):
            (d / "notes.md").write_text(NOTES, encoding="utf-8")
        art = out / "embedded.jpg"
        if extras.get("embedded_cover"):
            make_cover(art, "0x204060")
        for n, (fmt, title, artist) in enumerate(tracks, 1):
            codec_args, ext, _, _ = FORMATS[fmt]
            name = title + "." + ext if album_tags["album"] == "Sencillos" else f"{n:02d} {title}.{ext}"
            dest = d / name
            tags = {**album_tags, "title": title, "artist": artist or album_tags["album_artist"],
                    "track": f"{n}/{len(tracks)}", "disc": "1/1"}
            meta = [x for k, v in tags.items() for x in ("-metadata", f"{k}={v}")]
            inputs = ["-f", "lavfi", "-i", f"sine=frequency={freq}:duration={4 + n % 3}"]
            maps = []
            if extras.get("embedded_cover"):
                inputs += ["-i", str(art)]
                maps = ["-map", "0:a", "-map", "1:v", "-c:v", "mjpeg", "-disposition:v", "attached_pic"]
            ffmpeg(*inputs, *maps, *codec_args, *meta, str(dest))
            made.append((dest, fmt, album_tags["album_artist"]))
            freq += 110
        art.unlink(missing_ok=True)

    # Self-check: every file decodes with the expected codec and keeps its tags.
    for path, fmt, album_artist in made:
        codec, tags = probe(path)
        assert codec == FORMATS[fmt][2], (path, codec)
        if FORMATS[fmt][3]:
            assert tags.get("album_artist", tags.get("albumartist")) == album_artist, (path, tags)
            assert "title" in tags and "genre" in tags, (path, tags)
    print(f"{len(made)} tracks in {len(ALBUMS)} albums -> {lib}")


if __name__ == "__main__":
    main(Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent / "testlib"))
