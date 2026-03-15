package com.vocab.flashcard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 解析 Anki .colpkg 文件
 *
 * .colpkg 本质是 ZIP 压缩包，内含：
 * - collection.anki21：SQLite 数据库（可能为 deflate 压缩，ZIP 解压时自动解压）
 *
 * Anki 旗标在 cards.flags 中：flags % 8 → 0=无, 1=红, 2=橙, 3=绿, 4=蓝
 * 单词内容在 notes.flds 中，字段用 \u001f (ASCII 31) 分隔
 */
object ColpkgParser {

    private const val ANKI_DB_FILENAME = "collection.anki21"
    private const val ANKI_DB_LEGACY = "collection.anki2"
    private const val FIELD_SEPARATOR = '\u001f' // Anki 字段分隔符

    /**
     * 解析 .colpkg 文件，提取橙/红旗标单词，写入 repository
     * @return 解析出的单词数量
     */
    fun parse(context: Context, uri: Uri, repository: VocabRepository): Int {
        // 1. 从 ContentResolver 读取 ZIP 流
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("无法打开文件: $uri")

        // 2. 解压 ZIP，找到 collection.anki21 或 collection.anki2
        val dbFile = extractAnkiDb(inputStream, context)
        inputStream.close()

        // 3. 打开 SQLite 并查询
        val db = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        try {
            val words = queryFlaggedWords(db)
            db.close()

            // 4. 清空旧数据并插入新词
            repository.clearAndInsert(words)
            return words.size
        } finally {
            dbFile.delete()
        }
    }

    /**
     * 从 ZIP 流中解压出 Anki 数据库文件
     */
    private fun extractAnkiDb(zipStream: java.io.InputStream, context: Context): File {
        val zipInput = ZipInputStream(zipStream)
        var entry = zipInput.nextEntry
        var dbFile: File? = null

        // 临时目录
        val tempDir = File(context.cacheDir, "colpkg_extract")
        tempDir.mkdirs()

        while (entry != null) {
            val name = entry.name
            // 支持 anki21（新版）和 anki2（旧版）
            if (name == ANKI_DB_FILENAME || name == ANKI_DB_LEGACY) {
                dbFile = File(tempDir, "collection.db")
                FileOutputStream(dbFile).use { out ->
                    zipInput.copyTo(out)
                }
                break
            }
            entry = zipInput.nextEntry
        }

        zipInput.close()
        return dbFile ?: throw IllegalArgumentException("ZIP 中未找到 collection.anki21 或 collection.anki2")
    }

    /**
     * 查询 flags % 8 IN (1, 2) 的卡片对应的笔记
     * 1=红色旗标, 2=橙色旗标
     */
    private fun queryFlaggedWords(db: SQLiteDatabase): List<Pair<String, String>> {
        val result = mutableSetOf<Pair<String, String>>()

        // SQL: 筛选红/橙旗标，JOIN notes 获取 flds
        val sql = """
            SELECT DISTINCT n.flds
            FROM notes n
            INNER JOIN cards c ON c.nid = n.id
            WHERE (c.flags % 8) IN (1, 2)
        """.trimIndent()

        val cursor = db.rawQuery(sql, null)
        cursor.use {
            val fldsIndex = it.getColumnIndex("flds")
            if (fldsIndex < 0) return emptyList()

            while (it.moveToNext()) {
                val flds = it.getString(fldsIndex)
                parseFlds(flds)?.let { pair -> result.add(pair) }
            }
        }

        return result.toList()
    }

    /**
     * 解析 notes.flds：用 \u001f 分隔，通常 [0]=单词, [1]=词义
     * 同时去除 HTML 标签
     */
    private fun parseFlds(flds: String?): Pair<String, String>? {
        if (flds.isNullOrBlank()) return null
        val parts = flds.split(FIELD_SEPARATOR)
        if (parts.size < 2) return null

        val word = stripHtml(parts[0].trim())
        val meaning = stripHtml(parts[1].trim())
        if (word.isBlank()) return null

        return word to meaning
    }

    /**
     * 简单去除 HTML 标签
     */
    private fun stripHtml(html: String): String {
        return html.replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ")
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("\\s+"), " ").trim()
    }
}
