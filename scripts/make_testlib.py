#!/usr/bin/env python3
"""Generate a small synthetic music library with ffmpeg (no copyrighted audio).

Covers every format Spindle reads, multi-value genres, a compilation, a single,
a folder cover, an embedded cover, notes.md and a high-res file.

    python scripts/make_testlib.py [out_dir]     # default: testlib/
    python scripts/make_testlib.py --grande [N]  # testlib/Grande: N tracks (default 5000) for scan timing
"""
import argparse
import json
import os
import shutil
import subprocess
import zlib
from concurrent.futures import ThreadPoolExecutor
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


# --grande: one album = one format; weighted toward the common ones. WAV left out (no reliable tags).
BIG_FORMATS = ["flac", "flac", "mp3", "mp3", "aac", "opus", "alac", "vorbis"]
GENRES = ["rock", "pop", "electrónica", "jazz", "clásica", "ambient", "folk", "hip hop", "metal", "bso"]
WORDS = "luz ruido señal hielo norte marea vidrio eco sombra fuego río polvo cielo humo arena noche".split()


def make_big(out, n):
    """N tracks, 10 per album, 5 albums per artist, every 10th album a compilation.

    Each track is a tag-only remux (-c copy) of a 1 s template, so 5000 tracks stay small and fast.
    """
    lib, tpl = out / "Grande", out / "plantillas"
    shutil.rmtree(lib, ignore_errors=True)
    tpl.mkdir(parents=True, exist_ok=True)
    for fmt in set(BIG_FORMATS):
        codec_args, ext, _, _ = FORMATS[fmt]
        ffmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=1", *codec_args, str(tpl / f"{fmt}.{ext}"))
    make_cover(tpl / "cover.jpg", "0x404040")

    jobs, sample = [], {}
    for i in range(n):
        album, track = divmod(i, 10)
        fmt = BIG_FORMATS[album % len(BIG_FORMATS)]
        ext = FORMATS[fmt][1]
        comp = album % 10 == 9
        album_artist = "Varios artistas" if comp else f"Artista {album // 5:03d}"
        name = f"{WORDS[album % 16].capitalize()} {WORDS[album * 7 % 16]} {album:03d}"
        year = 1970 + album % 55
        genre = GENRES[album % 10] + (f";{GENRES[(album + 3) % 10]}" if album % 3 == 0 else "")
        d = lib / album_artist / f"{year} - {name}"
        if track == 0:
            d.mkdir(parents=True, exist_ok=True)
            if album % 2 == 0:
                shutil.copy(tpl / "cover.jpg", d / "cover.jpg")
        title = f"{WORDS[i % 16].capitalize()} {WORDS[i * 5 % 16]}"
        tags = {"album_artist": album_artist, "album": name, "date": year, "genre": genre, "title": title,
                "artist": f"Artista {i % 97:03d}" if comp else album_artist,
                "track": f"{track + 1}/{min(10, n - album * 10)}", "disc": "1/1"}
        if comp:
            tags["compilation"] = "1"
        meta = [x for k, v in tags.items() for x in ("-metadata", f"{k}={v}")]
        id3 = ["-id3v2_version", "4"] if fmt == "mp3" else []
        dest = d / f"{track + 1:02d} {title}.{ext}"
        jobs.append(("-i", str(tpl / f"{fmt}.{ext}"), "-map", "0", "-c", "copy", *id3, *meta, str(dest)))
        sample.setdefault(fmt, (dest, album_artist))
    with ThreadPoolExecutor(os.cpu_count()) as ex:
        list(ex.map(lambda a: ffmpeg(*a), jobs))

    # Self-check: N audio files; one per format keeps codec and tags after the copy remux.
    exts = {FORMATS[f][1] for f in BIG_FORMATS}
    count = sum(1 for p in lib.rglob("*") if p.suffix[1:] in exts)
    assert count == n, (count, n)
    for fmt, (path, album_artist) in sample.items():
        codec, tags = probe(path)
        assert codec == FORMATS[fmt][2], (path, codec)
        assert tags.get("album_artist", tags.get("albumartist")) == album_artist, (path, tags)
        assert "title" in tags and "genre" in tags, (path, tags)
    print(f"{n} tracks in {(n + 9) // 10} albums -> {lib}")


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Generate a synthetic test library with ffmpeg.")
    ap.add_argument("out", nargs="?", type=Path, default=Path(__file__).resolve().parent.parent / "testlib")
    ap.add_argument("--grande", type=int, nargs="?", const=5000, metavar="N",
                    help="large library for scan timing (default 5000 tracks)")
    a = ap.parse_args()
    make_big(a.out, a.grande) if a.grande else main(a.out)
