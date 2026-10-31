package com.devvault.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** 一行查询结果，按列名取值 */
class Row(private val values: Map<String, Any?>) {
    operator fun get(col: String): Any? = values[col]
    fun str(col: String): String = values[col]?.toString() ?: ""
    fun strOrNull(col: String): String? = values[col]?.toString()
    fun long(col: String): Long = longOrNull(col) ?: 0L
    fun longOrNull(col: String): Long? = when (val v = values[col]) {
        null -> null
        is Long -> v
        is Double -> v.toLong()
        else -> v.toString().toLongOrNull()
    }
    fun int(col: String): Int = long(col).toInt()
    fun bool(col: String): Boolean = long(col) != 0L
    val columns: Set<String> get() = values.keys
}

/**
 * SQLite 连接的轻量封装（接口风格接近桌面端 better-sqlite3）。
 * 所有访问都必须经过 [io]，保证同一时刻只有一个协程使用连接。
 */
class Db(path: String) {
    private val conn: SQLiteConnection = BundledSQLiteDriver().open(path)
    private var txDepth = 0

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

    suspend fun <T> io(block: Db.() -> T): T = withContext(dispatcher) { block() }

    private fun bind(st: SQLiteStatement, args: Array<out Any?>) {
        args.forEachIndexed { i, v ->
            val idx = i + 1
            when (v) {
                null -> st.bindNull(idx)
                is String -> st.bindText(idx, v)
                is Long -> st.bindLong(idx, v)
                is Int -> st.bindLong(idx, v.toLong())
                is Boolean -> st.bindLong(idx, if (v) 1 else 0)
                is Double -> st.bindDouble(idx, v)
                is ByteArray -> st.bindBlob(idx, v)
                else -> st.bindText(idx, v.toString())
            }
        }
    }

    fun exec(sql: String, vararg args: Any?) {
        conn.prepare(sql).use { st ->
            bind(st, args)
            st.step()
        }
    }

    fun query(sql: String, vararg args: Any?): List<Row> = conn.prepare(sql).use { st ->
        bind(st, args)
        val rows = ArrayList<Row>()
        val n = st.getColumnCount()
        val names = List(n) { st.getColumnName(it) }
        while (st.step()) {
            val m = LinkedHashMap<String, Any?>(n)
            for (i in 0 until n) {
                m[names[i]] = when (st.getColumnType(i)) {
                    SQLITE_INTEGER -> st.getLong(i)
                    SQLITE_FLOAT -> st.getDouble(i)
                    SQLITE_NULL -> null
                    SQLITE_BLOB -> st.getBlob(i)
                    else -> st.getText(i)
                }
            }
            rows.add(Row(m))
        }
        rows
    }

    fun one(sql: String, vararg args: Any?): Row? = query(sql, *args).firstOrNull()

    fun long(sql: String, vararg args: Any?): Long? = conn.prepare(sql).use { st ->
        bind(st, args)
        if (st.step() && !st.isNull(0)) st.getLong(0) else null
    }

    fun insert(sql: String, vararg args: Any?): Long {
        exec(sql, *args)
        return long("SELECT last_insert_rowid()") ?: 0L
    }

    fun changes(): Long = long("SELECT changes()") ?: 0L

    /** 事务，可嵌套（内层使用 SAVEPOINT） */
    fun <T> tx(block: () -> T): T {
        val sp = "sp$txDepth"
        if (txDepth == 0) exec("BEGIN IMMEDIATE") else exec("SAVEPOINT $sp")
        txDepth++
        try {
            val r = block()
            txDepth--
            if (txDepth == 0) exec("COMMIT") else exec("RELEASE $sp")
            return r
        } catch (e: Throwable) {
            txDepth--
            if (txDepth == 0) exec("ROLLBACK") else { exec("ROLLBACK TO $sp"); exec("RELEASE $sp") }
            throw e
        }
    }

    fun close() = conn.close()

    private companion object {
        const val SQLITE_INTEGER = 1
        const val SQLITE_FLOAT = 2
        const val SQLITE_BLOB = 4
        const val SQLITE_NULL = 5
    }
}
