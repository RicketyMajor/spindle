package dev.ricketymajor.spindle.library

fun ascii(s: String) = s.toByteArray(Charsets.US_ASCII)
fun le16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
fun le32(v: Int) = ByteArray(4) { i -> (v shr (8 * i)).toByte() }
fun be32(v: Int) = ByteArray(4) { i -> (v shr (24 - 8 * i)).toByte() }
fun be64(v: Long) = ByteArray(8) { i -> (v shr (56 - 8 * i)).toByte() }

/** fLaC + a last-block STREAMINFO for 44.1 kHz stereo at [bits]; [id3] puts a 15-byte ID3v2 tag in front. */
fun flac(bits: Int, id3: Boolean = false): ByteArray {
    val info = ByteArray(34)
    info[10] = 0x0A; info[11] = 0xC4.toByte()                  // 44100 = 0x0AC44 over 20 bits…
    info[12] = (0x40 or 0x02 or ((bits - 1) shr 4)).toByte()    // …its last nibble, channels-1 = 1, bit depth's high bit
    info[13] = (((bits - 1) and 0x0F) shl 4).toByte()           // bit depth's low nibble
    val body = ascii("fLaC") + byteArrayOf(0x80.toByte(), 0, 0, 34) + info
    return if (id3) ascii("ID3") + byteArrayOf(4, 0, 0, 0, 0, 0, 5) + ByteArray(5) + body else body
}

/** RIFF/WAVE with an odd-sized LIST chunk before `fmt ` (tests the pad byte) unless [extraChunk] is false. */
fun wav(bits: Int, extraChunk: Boolean = true): ByteArray {
    val list = if (extraChunk) ascii("LIST") + le32(5) + ByteArray(6) else ByteArray(0)
    val fmt = ascii("fmt ") + le32(16) + le16(1) + le16(2) + le32(96000) + le32(96000 * 2 * bits / 8) + le16(2 * bits / 8) + le16(bits)
    val body = ascii("WAVE") + list + fmt + ascii("data") + le32(0)
    return ascii("RIFF") + le32(body.size) + body
}

/** An MP4 box: 32-bit size + type + children. */
fun box(type: String, vararg parts: ByteArray): ByteArray {
    val body = parts.fold(ByteArray(0)) { acc, p -> acc + p }
    return be32(8 + body.size) + ascii(type) + body
}

/** `alac` sample entry: 28-byte AudioSampleEntry, then the `alac` full box whose cookie has the bit depth at byte 5. */
fun alacEntry(bits: Int) = box("alac", ByteArray(28), box("alac", ByteArray(4), ByteArray(5) + byteArrayOf(bits.toByte()) + ByteArray(18)))
fun mp4aEntry() = box("mp4a", ByteArray(28), box("esds", ByteArray(30)))

/** ftyp + mdat + moov (or moov before mdat); [largeMdat] writes mdat with a 64-bit size. */
fun m4a(entry: ByteArray, moovFirst: Boolean = false, largeMdat: Boolean = false): ByteArray {
    val stsd = box("stsd", ByteArray(4), be32(1), entry)
    val moov = box("moov", box("mvhd", ByteArray(100)),
        box("trak", box("tkhd", ByteArray(84)), box("mdia", box("mdhd", ByteArray(24)), box("minf", box("stbl", stsd)))))
    val ftyp = box("ftyp", ascii("M4A "), ByteArray(4))
    val mdat = if (largeMdat) be32(1) + ascii("mdat") + be64(16L + 500) + ByteArray(500) else box("mdat", ByteArray(500))
    return if (moovFirst) ftyp + moov + mdat else ftyp + mdat + moov
}
