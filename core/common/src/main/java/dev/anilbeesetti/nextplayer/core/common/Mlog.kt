package dev.anilbeesetti.nextplayer.core.common

import java.io.ByteArrayOutputStream

/**
 * A compact binary log file.
 *
 * A text log repeats the tag and the full timestamp on every line, which is most of its size over a
 * long playback session. Here each tag is written once and then referred to by a small number, and
 * every timestamp is stored as the gap from the previous entry. Both numbers go through a varint,
 * so a gap of 40 ms costs one byte instead of thirteen characters.
 *
 * Layout:
 *   header   'M' 'L' 'O' 'G' <version byte>
 *   tag      0x01 <varint id> <varint length> <utf8 bytes>
 *   entry    0x02 <varint deltaMs> <varint tagId> <level byte> <varint length> <utf8 bytes>
 *
 * Deliberately free of Android types, so the format can be checked on a plain JVM.
 */
object Mlog {

    const val VERSION: Int = 1

    private const val MAGIC = "MLOG"
    private const val KIND_TAG = 0x01
    private const val KIND_ENTRY = 0x02

    /** One log line, before and after the trip through the file. */
    data class Entry(
        val timeMs: Long,
        val tag: String,
        val level: Int,
        val message: String,
    )

    /** Appends entries and keeps the tag table and the previous timestamp. */
    class Writer {
        private val out = ByteArrayOutputStream(4096)
        private val tagIds = LinkedHashMap<String, Int>()
        private var lastTimeMs = 0L

        init {
            MAGIC.forEach { out.write(it.code) }
            out.write(VERSION)
        }

        fun write(
            timeMs: Long,
            tag: String,
            level: Int,
            message: String,
        ): Writer {
            val id = tagIds[tag] ?: run {
                val next = tagIds.size
                tagIds[tag] = next
                out.write(KIND_TAG)
                writeVarint(out, next.toLong())
                writeString(out, tag)
                next
            }
            out.write(KIND_ENTRY)
            writeVarint(out, (timeMs - lastTimeMs).coerceAtLeast(0L))
            writeVarint(out, id.toLong())
            out.write(level.coerceIn(0, 4))
            writeString(out, message)
            lastTimeMs = timeMs
            return this
        }

        fun toByteArray(): ByteArray = out.toByteArray()

        val size: Int
            get() = out.size()
    }

    /** Reads back what [Writer] produced; throws when the bytes are not a log. */
    fun read(bytes: ByteArray): List<Entry> {
        val cursor = Cursor(bytes)
        for (k in MAGIC.indices) {
            require(cursor.byte() == MAGIC[k].code) { "not a log file" }
        }
        val version = cursor.byte()
        require(version == VERSION) { "unsupported log version $version" }

        val tags = ArrayList<String>()
        val entries = ArrayList<Entry>()
        var time = 0L
        while (!cursor.done) {
            when (cursor.byte()) {
                KIND_TAG -> {
                    val id = cursor.varint().toInt()
                    val tag = cursor.string()
                    require(id == tags.size) { "tag ids must be dense" }
                    tags.add(tag)
                }

                KIND_ENTRY -> {
                    val delta = cursor.varint()
                    val tagId = cursor.varint().toInt()
                    val level = cursor.byte()
                    val message = cursor.string()
                    time += delta
                    entries.add(
                        Entry(
                            timeMs = time,
                            tag = tags[tagId],
                            level = level,
                            message = message,
                        ),
                    )
                }

                else -> error("unknown record in log")
            }
        }
        return entries
    }

    /** The same entries as plain text, so the saving can be measured. */
    fun asText(entries: List<Entry>): String = entries.joinToString("\n") { e ->
        "${e.timeMs} [${e.level}] ${e.tag}: ${e.message}"
    }

    private class Cursor(private val bytes: ByteArray) {
        private var i = 0

        val done: Boolean
            get() = i >= bytes.size

        fun byte(): Int {
            require(i < bytes.size) { "log ended early" }
            return bytes[i++].toInt() and 0xFF
        }

        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                val b = byte()
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
                require(shift <= 63) { "varint too long" }
            }
        }

        fun string(): String {
            val length = varint().toInt()
            require(length >= 0 && i + length <= bytes.size) { "bad string length" }
            val s = String(bytes, i, length, Charsets.UTF_8)
            i += length
            return s
        }
    }

    private fun writeString(
        out: ByteArrayOutputStream,
        value: String,
    ) {
        val raw = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, raw.size.toLong())
        out.write(raw)
    }

    private fun writeVarint(
        out: ByteArrayOutputStream,
        value: Long,
    ) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }
}
