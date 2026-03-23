package com.vocab.flashcard

import android.database.sqlite.SQLiteDatabase
import com.github.luben.zstd.ZstdInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
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

    private const val TAG = "ColpkgParser"
    private const val FIELD_SEP = '\u001f'  // 0x1f
    private const val FLAG_RED = 1
    private const val FLAG_ORANGE = 2
    private const val SQLITE_HEADER = "SQLite format 3\u0000"
    private val HTML_TAG_REGEX = Regex("<[^>]+>")

    /**
     * 从 colpkg 中解析橙/红旗标单词
     * @param colpkgPath colpkg 文件路径
     * @return List<Pair<word, meaning>>
     * @throws IOException 当词库损坏或数据库格式异常时抛出
     */
    fun parseFlaggedWords(colpkgPath: String, workDir: File): List<Pair<String, String>> {
        AppLog.info(TAG, "Parse started: $colpkgPath")
        ZipFile(File(colpkgPath)).use { zip ->
            val dbEntry = findDatabaseEntry(zip) ?: return emptyList()
            val tempDb = File.createTempFile("anki_col", ".db", workDir)
            try {
                extractDatabase(zip, dbEntry, tempDb)
                val words = parseFlaggedFromDb(tempDb.absolutePath)
                AppLog.info(TAG, "Parsed ${words.size} flagged words from ${dbEntry.name}")
                return words
            } catch (e: Throwable) {
                AppLog.error(TAG, "Failed to parse $colpkgPath", e)
                if (e is IOException) throw e
                throw IOException("解析词库失败: ${e.message ?: "未知错误"}", e)
            } finally {
                tempDb.delete()
            }
        }
    }

    /**
     * 优先级：anki21b(最新) > anki21(Legacy2) > anki2(Legacy1)
     */
    private fun findDatabaseEntry(zip: ZipFile): ZipEntry? {
        return zip.entries().toList()
            .filter { entry ->
                !entry.isDirectory && (
                    entry.name.endsWith(".anki2") ||
                        entry.name.endsWith(".anki21") ||
                        entry.name.endsWith(".anki21b")
                    )
            }
            .maxByOrNull { entry ->
                when {
                    entry.name.endsWith(".anki21b") -> 2
                    entry.name.endsWith(".anki21") -> 1
                    else -> 0
                }
            }
    }

    private fun extractDatabase(zip: ZipFile, dbEntry: ZipEntry, tempDb: File) {
        if (dbEntry.name.endsWith(".anki21b")) {
            if (tryExtractRawDatabase(zip, dbEntry, tempDb)) {
                AppLog.info(TAG, "Detected raw SQLite data inside ${dbEntry.name}")
                return
            }
            AppLog.info(TAG, "Raw read failed, retrying ${dbEntry.name} with zstd")
            if (tryExtractZstdDatabase(zip, dbEntry, tempDb)) {
                AppLog.info(TAG, "Zstd decompression succeeded for ${dbEntry.name}")
                return
            }
            throw IOException("无法解码 ${dbEntry.name}，既不是 SQLite 数据，也无法按 zstd 解压")
        }
        copyEntryToFile(zip, dbEntry, tempDb)
        validateSqliteDatabase(tempDb, dbEntry.name)
    }

    private fun tryExtractRawDatabase(zip: ZipFile, dbEntry: ZipEntry, tempDb: File): Boolean {
        return try {
            copyEntryToFile(zip, dbEntry, tempDb)
            validateSqliteDatabase(tempDb, dbEntry.name)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun tryExtractZstdDatabase(zip: ZipFile, dbEntry: ZipEntry, tempDb: File): Boolean {
        return try {
            zip.getInputStream(dbEntry).use { rawInput ->
                ZstdInputStream(BufferedInputStream(rawInput)).use { input ->
                    tempDb.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            validateSqliteDatabase(tempDb, dbEntry.name)
            true
        } catch (t: UnsatisfiedLinkError) {
            AppLog.error(TAG, "Zstd native library is missing from APK", t)
            throw IOException("APK 未包含 zstd 原生库，请重新安装修复后的版本", t)
        } catch (t: Throwable) {
            AppLog.error(TAG, "Zstd decode failed for ${dbEntry.name}", t)
            false
        }
    }

    private fun copyEntryToFile(zip: ZipFile, dbEntry: ZipEntry, target: File) {
        zip.getInputStream(dbEntry).use { input ->
            target.outputStream().buffered().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun validateSqliteDatabase(tempDb: File, entryName: String) {
        if (tempDb.length() == 0L) {
            throw IOException("词库数据库为空: $entryName")
        }
        val header = ByteArray(SQLITE_HEADER.length)
        tempDb.inputStream().use { input ->
            val read = input.read(header)
            if (read != header.size || header.decodeToString() != SQLITE_HEADER) {
                throw IOException("词库数据库格式无效: $entryName")
            }
        }
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

            // cards.flags: 低 3 位表示旗标颜色，1=红、2=橙
            // notes.flds: 字段用 0x1f 分隔，通常 [0]=单词 [1]=词义
            val cursor = db.rawQuery(
                """
                SELECT n.flds FROM notes n
                INNER JOIN cards c ON c.nid = n.id
                WHERE (c.flags & 7) IN (?, ?)
                """.trimIndent(),
                arrayOf(FLAG_RED.toString(), FLAG_ORANGE.toString())
            )
            cursor.use {
                val fldsIdx = it.getColumnIndex("flds")
                if (fldsIdx < 0) return emptyList()
                while (it.moveToNext()) {
                    val flds = it.getString(fldsIdx) ?: continue
                    val parts = flds.split(FIELD_SEP)
                    val word = parts.getOrNull(0)?.stripHtml()?.trim() ?: continue
                    val meaning = parts.getOrNull(1)?.stripHtml()?.trim().orEmpty()
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

    private fun String.stripHtml(): String = replace(HTML_TAG_REGEX, "")
}
