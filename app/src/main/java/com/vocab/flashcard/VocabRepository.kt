package com.vocab.flashcard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 本地词库存储
 * 将解析出的橙/红旗标单词缓存到 SQLite，供定时弹窗随机抽取
 */
class VocabRepository(context: Context) : SQLiteOpenHelper(
    context,
    "vocab_flashcard.db",
    null,
    1
) {

    companion object {
        private const val TABLE = "flagged_words"
        private const val COL_ID = "id"
        private const val COL_WORD = "word"
        private const val COL_MEANING = "meaning"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_WORD TEXT NOT NULL,
                $COL_MEANING TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /**
     * 清空表并批量插入新词
     */
    fun clearAndInsert(words: List<Pair<String, String>>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM $TABLE")
            val stmt = db.compileStatement(
                "INSERT INTO $TABLE ($COL_WORD, $COL_MEANING) VALUES (?, ?)"
            )
            for ((word, meaning) in words) {
                stmt.bindString(1, word)
                stmt.bindString(2, meaning)
                stmt.executeInsert()
                stmt.clearBindings()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 随机获取一个单词
     * @return Pair(word, meaning) 或 null（无词时）
     */
    fun getRandomWord(): Pair<String, String>? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COL_WORD, $COL_MEANING FROM $TABLE ORDER BY RANDOM() LIMIT 1",
            null
        )
        cursor.use {
            return if (it.moveToFirst()) {
                val word = it.getString(0)
                val meaning = it.getString(1)
                Pair(word, meaning)
            } else null
        }
    }

    /**
     * 词库是否为空
     */
    fun isEmpty(): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE", null)
        cursor.use {
            it.moveToFirst()
            return it.getInt(0) == 0
        }
    }
}
