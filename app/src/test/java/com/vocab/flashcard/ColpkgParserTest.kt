package com.vocab.flashcard

import android.os.Build
import com.github.luben.zstd.Zstd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ColpkgParser 单元测试
 * 使用 Robolectric 模拟 Android SQLite 环境
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ColpkgParserTest {

    private val fieldSep = '\u001f'

    @Test
    fun parseFlaggedWords_emptyZip_returnsEmptyList() {
        val zipFile = createZipWithNoAnkiDb()
        try {
            val result = ColpkgParser.parseFlaggedWords(zipFile.absolutePath)
            assertTrue(result.isEmpty())
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun parseFlaggedWords_validAnki2_returnsFlaggedWords() {
        val zipFile = createColpkgWithAnki2(
            listOf(
                "apple" to "苹果",
                "banana" to "香蕉"
            )
        )
        try {
            val result = ColpkgParser.parseFlaggedWords(zipFile.absolutePath)
            assertEquals(2, result.size)
            assertEquals("apple", result[0].first)
            assertEquals("苹果", result[0].second)
            assertEquals("banana", result[1].first)
            assertEquals("香蕉", result[1].second)
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun parseFlaggedWords_validAnki21b_returnsFlaggedWords() {
        val zipFile = createColpkgWithAnki21b(
            listOf("hello" to "你好")
        )
        try {
            val result = ColpkgParser.parseFlaggedWords(zipFile.absolutePath)
            assertEquals(1, result.size)
            assertEquals("hello", result[0].first)
            assertEquals("你好", result[0].second)
        } finally {
            zipFile.delete()
        }
    }

    @Test(expected = java.io.IOException::class)
    fun parseFlaggedWords_invalidZstdMagic_throwsIOException() {
        val zipFile = createZipWithInvalidAnki21b()
        try {
            ColpkgParser.parseFlaggedWords(zipFile.absolutePath)
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun parseFlaggedWords_stripsHtmlTags() {
        val zipFile = createColpkgWithAnki2(
            listOf("<b>word</b>" to "<i>meaning</i>")
        )
        try {
            val result = ColpkgParser.parseFlaggedWords(zipFile.absolutePath)
            assertEquals(1, result.size)
            assertEquals("word", result[0].first)
            assertEquals("meaning", result[0].second)
        } finally {
            zipFile.delete()
        }
    }

    @Test
    fun parseFlaggedWords_supportsFlagBitmask() {
        val zipFile = createColpkgWithAnki2(
            words = listOf(
                "apple" to "苹果",
                "orange" to "橙子",
                "skip" to "跳过"
            ),
            flags = listOf(9, 10, 8)
        )
        try {
            val result = ColpkgParser.parseFlaggedWords(zipFile.absolutePath)
            assertEquals(2, result.size)
            assertTrue(result.contains("apple" to "苹果"))
            assertTrue(result.contains("orange" to "橙子"))
        } finally {
            zipFile.delete()
        }
    }

    private fun createZipWithNoAnkiDb(): File {
        val zipFile = File.createTempFile("test_empty", ".colpkg")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("media"))
            zos.write(byteArrayOf(0))
            zos.closeEntry()
        }
        return zipFile
    }

    private fun createColpkgWithAnki2(
        words: List<Pair<String, String>>,
        flags: List<Int> = List(words.size) { 1 }
    ): File {
        val dbBytes = createMinimalAnki2Db(words, flags)
        val zipFile = File.createTempFile("test_anki2", ".colpkg")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("collection.anki2"))
            zos.write(dbBytes)
            zos.closeEntry()
        }
        return zipFile
    }

    private fun createColpkgWithAnki21b(words: List<Pair<String, String>>): File {
        val dbBytes = createMinimalAnki2Db(words, List(words.size) { 1 })
        val compressed = Zstd.compress(dbBytes)
        val zipFile = File.createTempFile("test_anki21b", ".colpkg")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("collection.anki21b"))
            zos.write(compressed)
            zos.closeEntry()
        }
        return zipFile
    }

    private fun createZipWithInvalidAnki21b(): File {
        val invalidData = byteArrayOf(0x00, 0x00, 0x00, 0x00)
        val zipFile = File.createTempFile("test_invalid", ".colpkg")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("collection.anki21b"))
            zos.write(invalidData)
            zos.closeEntry()
        }
        return zipFile
    }

    private fun createMinimalAnki2Db(words: List<Pair<String, String>>, flags: List<Int>): ByteArray {
        require(words.size == flags.size) { "words 与 flags 数量必须一致" }
        val dbFile = File.createTempFile("anki2", ".db")
        try {
            val db = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(
                dbFile.absolutePath,
                null
            )
            db.execSQL("""
                CREATE TABLE notes (id INTEGER PRIMARY KEY, flds TEXT)
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE cards (id INTEGER PRIMARY KEY, nid INTEGER, flags INTEGER)
            """.trimIndent())
            words.forEachIndexed { index, (word, meaning) ->
                val noteId = index + 1L
                val flds = "$word$fieldSep$meaning"
                db.execSQL(
                    "INSERT INTO notes (id, flds) VALUES (?, ?)",
                    arrayOf(noteId, flds)
                )
                db.execSQL(
                    "INSERT INTO cards (id, nid, flags) VALUES (?, ?, ?)",
                    arrayOf(noteId, noteId, flags[index])
                )
            }
            db.close()
            return dbFile.readBytes()
        } finally {
            dbFile.delete()
        }
    }
}
