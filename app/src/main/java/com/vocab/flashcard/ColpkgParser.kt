package com.vocab.flashcard

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.zip.ZipFile

/**
 * 解析 Anki .colpkg 文件，筛选橙/红旗标单词
 *
 * Anki 旗标：cards.flags % 8 = 0无/1红/2橙/3绿/4蓝
 * notes.flds 用 0x1f 分隔各字段，通常 [0]=单词 [1]=词义
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
        val dbEntry = zip.entries().toList().find { e ->
            !e.isDirectory && (e.name.endsWith(".anki2") || e.name.endsWith(".anki21"))
        } ?: run {
            zip.close()
            return emptyList()
        }

        val tempDb = File.createTempFile("anki_col", ".db")
        try {
            zip.getInputStream(dbEntry).use { input ->
                tempDb.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return parseFlaggedFromDb(tempDb.absolutePath)
        } finally {
            tempDb.delete()
            zip.close()
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

            // cards.flags % 8: 1=红, 2=橙（Anki 旗标定义）
            // notes.flds: 字段用 0x1f 分隔，通常 [0]=单词 [1]=词义
            val cursor = db.rawQuery(
                """
                SELECT n.flds FROM notes n
                INNER JOIN cards c ON c.nid = n.id
                WHERE (c.flags % 8) IN ($FLAG_RED, $FLAG_ORANGE)
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
