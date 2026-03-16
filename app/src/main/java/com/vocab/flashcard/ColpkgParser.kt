package com.vocab.flashcard

import android.database.sqlite.SQLiteDatabase
import com.github.luben.zstd.Zstd
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * 解析 Anki .colpkg 文件，筛选橙/红旗标单词
 *
 * 支持三种格式：
 * - collection.anki21b（Anki 24 默认，zstd 压缩）
 * - collection.anki21（Legacy 2，勾选「支持旧版」时）
 * - collection.anki2（Legacy 1）
 *
 * Anki 旗标：cards.flags 1=红 2=橙
 * notes.flds 用 0x1f 分隔，通常 [0]=单词 [1]=词义
 */
object ColpkgParser {

    private const val FIELD_SEP = '\u001f'  // 0x1f
    private const val FLAG_RED = 1
    private const val FLAG_ORANGE = 2

    /**
     * 从 colpkg 中解析橙/红旗标单词
     * @param colpkgPath colpkg 文件路径
     * @return List<Pair<word, meaning>>，失败返回空列表
     */
    fun parseFlaggedWords(colpkgPath: String): List<Pair<String, String>> {
        val zip = ZipFile(File(colpkgPath))
        // 优先级：anki21b(最新) > anki21(Legacy2) > anki2(Legacy1)
        val dbEntry = zip.entries().toList()
            .filter { e ->
                !e.isDirectory && (e.name.endsWith(".anki2") ||
                    e.name.endsWith(".anki21") ||
                    e.name.endsWith(".anki21b"))
            }
            .maxByOrNull { e ->
                when {
                    e.name.endsWith(".anki21b") -> 2
                    e.name.endsWith(".anki21") -> 1
                    else -> 0
                }
            }
            ?: run {
                zip.close()
                return emptyList()
            }

        val tempDb = File.createTempFile("anki_col", ".db")
        try {
            val compressed = zip.getInputStream(dbEntry).use { it.readBytes() }
            val dbBytes = if (dbEntry.name.endsWith(".anki21b")) {
                decompressZstd(compressed)
            } else {
                compressed
            }
            tempDb.writeBytes(dbBytes)
            return parseFlaggedFromDb(tempDb.absolutePath)
        } finally {
            tempDb.delete()
            zip.close()
        }
    }

    /**
     * 解压 zstd 数据，带错误校验，避免原生崩溃导致进程退出
     */
    private fun decompressZstd(compressed: ByteArray): ByteArray {
        if (compressed.size < 4) throw IOException("数据过短，非有效 zstd 格式")
        // zstd 魔数: 0x28 0xB5 0x2F 0xFD
        if (compressed[0].toInt() and 0xFF != 0x28 || compressed[1].toInt() and 0xFF != 0xB5 ||
            compressed[2].toInt() and 0xFF != 0x2F || compressed[3].toInt() and 0xFF != 0xFD) {
            throw IOException("非 zstd 压缩格式，请确认词库为 Anki 24 导出的 .colpkg")
        }
        val maxSize = 128 * 1024 * 1024  // 128MB，兼顾大词库与低端机
        val buffer = ByteArray(maxSize)
        val len = Zstd.decompress(buffer, compressed)
        if (Zstd.isError(len)) {
            throw IOException("Zstd 解压失败: ${Zstd.getErrorName(len)}")
        }
        if (len <= 0 || len > maxSize) {
            throw IOException("解压结果异常: size=$len")
        }
        return buffer.copyOf(len.toInt())
    }

    private fun parseFlaggedFromDb(dbPath: String): List<Pair<String, String>> {
        val db = SQLiteDatabase.openDatabase(
            dbPath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        try {
            val result = mutableListOf<Pair<String, String>>()
            val seen = mutableSetOf<String>()

            // cards.flags: 1=红, 2=橙（新版直接存 1-7；旧版用 flags%8）
            // notes.flds: 字段用 0x1f 分隔，通常 [0]=单词 [1]=词义
            val cursor = db.rawQuery(
                """
                SELECT n.flds FROM notes n
                INNER JOIN cards c ON c.nid = n.id
                WHERE (c.flags = $FLAG_RED OR c.flags = $FLAG_ORANGE OR (c.flags % 8) IN ($FLAG_RED, $FLAG_ORANGE))
                """.trimIndent(),
                null
            )
            cursor.use {
                val fldsIdx = it.getColumnIndex("flds")
                if (fldsIdx < 0) return emptyList()
                while (it.moveToNext()) {
                    val flds = it.getString(fldsIdx) ?: continue
                    val parts = flds.split(FIELD_SEP)
                    val word = parts.getOrNull(0)?.trim()?.replace(Regex("<[^>]+>"), "") ?: continue
                    val meaning = parts.getOrNull(1)?.trim()?.replace(Regex("<[^>]+>"), "") ?: ""
                    if (word.isNotBlank() && word !in seen) {
                        seen.add(word)
                        result.add(Pair(word, meaning))
                    }
                }
            }
            return result
        } finally {
            db.close()
        }
    }
}
