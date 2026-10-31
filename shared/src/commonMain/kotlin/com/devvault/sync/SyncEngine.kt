package com.devvault.sync

import com.devvault.crypto.E2ee
import com.devvault.crypto.LocalCipher
import com.devvault.data.Db
import com.devvault.data.Repository
import com.devvault.data.Row
import com.devvault.data.Schema
import com.devvault.model.ExtraField
import com.devvault.util.Dates
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * 云同步引擎（端到端加密），移植自桌面端 src/main/sync.ts，协议见桌面端 docs/server-api.md。
 *
 * - 本地修改由数据库触发器记录（sync_dirty / sync_tombstones），业务代码无需关心同步
 * - 一轮同步 = 拉取 → 合并 → 推送；推送遇到冲突时重新拉取后再推，最多 3 轮
 * - 冲突以记录为单位按修改时间「后写者胜」；删除与修改冲突时删除优先
 * - 记录明文结构与桌面端完全一致，两端可共用同一账号
 */
class SyncEngine(
    private val db: Db,
    private val repo: Repository,
    private val cloud: Cloud,
    private val scope: CoroutineScope,
    /** 云端数据写入本地后回调，用于刷新界面 */
    private val onRemoteChange: () -> Unit,
) {
    private object Key {
        const val CURSOR = "sync.cursor"
        const val ACCOUNT = "sync.account"
    }

    private val PAGE = 500
    private val MAX_ROUNDS = 3
    private val INTERVAL_MS = 2 * 60_000L
    private val DEBOUNCE_MS = 3_000L

    private val TABLE = Schema.SYNC_TABLES.associate { it.kind to it.table }
    private val ORDER = listOf("priority", "status", "project", "entry", "todo", "comment")
    private fun isKind(k: String) = k in TABLE

    private val json get() = cloud.json
    private val mutex = Mutex()
    private var rerun = false
    private var debounceJob: Job? = null
    private var intervalJob: Job? = null

    // ---------- 服务端接口 ----------

    @Serializable
    private class RemoteItem(
        val kind: String,
        val id: String,
        val revision: Long,
        val deleted: Boolean,
        val modifiedAt: Long,
        val payload: String? = null,
    )

    @Serializable
    private class PullResponse(val items: List<RemoteItem>, val revision: Long, val hasMore: Boolean)

    @Serializable
    private class ItemRef(val kind: String, val id: String, val revision: Long)

    @Serializable
    private class PushResponse(val accepted: List<ItemRef>, val conflicts: List<ItemRef>, val revision: Long = 0)

    @Serializable
    private class Outgoing(
        val kind: String,
        val id: String,
        val baseRevision: Long,
        val deleted: Boolean,
        val modifiedAt: Long,
        val payload: String?,
    )

    @Serializable
    private class PushRequest(val changes: List<Outgoing>)

    // ---------- 工具 ----------

    private fun uidOf(kind: String, id: Long?): String? =
        id?.let { db.one("SELECT uid FROM ${TABLE[kind]} WHERE id = ?", it)?.strOrNull("uid") }

    private fun idOf(kind: String, uid: String?): Long? =
        uid?.let { db.long("SELECT id FROM ${TABLE[kind]} WHERE uid = ?", it) }

    private fun firstStatus(done: Boolean): Long? {
        for (k in if (done) listOf("done") else listOf("todo", "active")) {
            db.long("SELECT id FROM statuses WHERE kind = ? ORDER BY sort_order, id LIMIT 1", k)?.let { return it }
        }
        return null
    }

    private fun defaultPriority(): Long? = db.long("SELECT id FROM priorities ORDER BY is_default DESC, sort_order, id LIMIT 1")

    private fun nowLocal(): String = db.one("SELECT datetime('now','localtime') AS t")!!.str("t")

    /** 本地记录 → 明文内容（解开本机加密的字段，把关联 id 转成 uid）。键名与桌面端一致 */
    private fun serialize(kind: String, r: Row): JsonObject = when (kind) {
        "priority" -> buildJsonObject {
            put("label", r.str("label")); put("name", r.str("name")); put("color", r.str("color"))
            put("sort_order", r.long("sort_order")); put("is_default", r.bool("is_default"))
        }
        "status" -> buildJsonObject {
            put("name", r.str("name")); put("color", r.str("color")); put("kind", r.str("kind")); put("sort_order", r.long("sort_order"))
        }
        "project" -> buildJsonObject {
            put("name", r.str("name")); put("color", r.str("color")); put("description", r.str("description"))
            put("sort_order", r.long("sort_order"))
        }
        "entry" -> {
            val extra = try {
                json.decodeFromString(ListSerializer(ExtraField.serializer()), r.str("extra").ifEmpty { "[]" })
            } catch (e: Exception) {
                emptyList()
            }
            buildJsonObject {
                put("project", uidOf("project", r.longOrNull("project_id")))
                put("category", r.str("category"))
                put("title", r.str("title"))
                put("env", r.str("env"))
                put("url", r.str("url"))
                put("username", r.str("username"))
                put("password", LocalCipher.decrypt(r.str("password")))
                put("verify_code", r.str("verify_code"))
                put("totp", LocalCipher.decrypt(r.str("totp")))
                put("expires_at", r.str("expires_at"))
                put("notes", r.str("notes"))
                put("tags", r.str("tags"))
                put("extra", buildJsonArray {
                    extra.forEach { f ->
                        add(buildJsonObject {
                            put("key", f.key)
                            put("secret", f.secret)
                            put("value", if (f.secret) LocalCipher.decrypt(f.value) else f.value)
                        })
                    }
                })
                put("favorite", r.bool("favorite"))
                put("created_at", r.str("created_at"))
                put("updated_at", r.str("updated_at"))
            }
        }
        "todo" -> buildJsonObject {
            put("project", uidOf("project", r.longOrNull("project_id")))
            put("parent", uidOf("todo", r.longOrNull("parent_id")))
            put("priority", uidOf("priority", r.longOrNull("priority")))
            put("status", uidOf("status", r.longOrNull("status_id")))
            put("title", r.str("title"))
            put("notes", r.str("notes"))
            put("done", r.bool("done"))
            put("done_at", r.strOrNull("done_at"))
            put("start_date", r.str("start_date"))
            put("due_date", r.str("due_date"))
            put("remind_at", r.str("remind_at"))
            put("tags", r.str("tags"))
            put("repeat", r.str("repeat"))
            put("created_at", r.str("created_at"))
            put("updated_at", r.str("updated_at"))
        }
        "comment" -> buildJsonObject {
            put("todo", uidOf("todo", r.longOrNull("todo_id")) ?: "")
            put("content", r.str("content"))
            put("created_at", r.str("created_at"))
        }
        else -> JsonObject(emptyMap())
    }

    /** 按 uid 插入或覆盖本地记录，并记下服务端版本；extraSet 为覆盖时额外执行的赋值 */
    private fun upsert(kind: String, item: RemoteItem, data: Map<String, Any?>, extraSet: List<String> = emptyList()) {
        val table = TABLE[kind]!!
        val cols = data.keys.toList()
        val sets = cols.map { "$it = excluded.$it" } + extraSet
        db.exec(
            """INSERT INTO $table (uid, ${cols.joinToString(", ")}, sync_rev, sync_dirty, sync_mtime)
               VALUES (?, ${cols.joinToString(", ") { "?" }}, ?, 0, ?)
               ON CONFLICT(uid) DO UPDATE SET ${sets.joinToString(", ")},
                 sync_rev = excluded.sync_rev, sync_dirty = 0, sync_mtime = excluded.sync_mtime""",
            item.id, *cols.map { data[it] }.toTypedArray(), item.revision, item.modifiedAt,
        )
    }

    private fun JsonObject.s(k: String, fallback: String = ""): String = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fallback
    private fun JsonObject.sOrNull(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.n(k: String): Long = (this[k] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toLong() ?: 0
    private fun JsonObject.b(k: String): Boolean = (this[k] as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.doubleOrNull?.let { d -> d != 0.0 }) } ?: false

    /** 云端内容写入本地（调用方已开启 sync_apply，触发器不会把这些写入当作本地修改） */
    private fun applyUpsert(kind: String, item: RemoteItem, d: JsonObject, parents: MutableMap<String, String?>) {
        when (kind) {
            "priority" -> upsert(kind, item, mapOf(
                "label" to d.s("label", "?"), "name" to d.s("name"), "color" to d.s("color", "#3b6cff"),
                "sort_order" to d.n("sort_order"), "is_default" to d.b("is_default"),
            ))
            "status" -> upsert(kind, item, mapOf(
                "name" to d.s("name", "?"), "color" to d.s("color", "#8a919e"),
                "kind" to d.s("kind").takeIf { it in listOf("todo", "active", "done") }.orEmpty().ifEmpty { "todo" },
                "sort_order" to d.n("sort_order"),
            ))
            "project" -> upsert(kind, item, mapOf(
                "name" to d.s("name", "未命名项目"), "color" to d.s("color", "#3b6cff"),
                "description" to d.s("description"), "sort_order" to d.n("sort_order"),
            ))
            "entry" -> {
                val extra = (d["extra"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { f ->
                    val secret = f.b("secret")
                    ExtraField(key = f.s("key"), secret = secret, value = if (secret) LocalCipher.encrypt(f.s("value")) else f.s("value"))
                }
                val now = nowLocal()
                upsert(kind, item, mapOf(
                    "project_id" to idOf("project", d.sOrNull("project")),
                    "category" to d.s("category", "other"),
                    "title" to d.s("title", "未命名"),
                    "env" to d.s("env"),
                    "url" to d.s("url"),
                    "username" to d.s("username"),
                    "password" to LocalCipher.encrypt(d.s("password")),
                    "verify_code" to d.s("verify_code"),
                    "totp" to LocalCipher.encrypt(d.s("totp")),
                    "expires_at" to d.s("expires_at"),
                    "notes" to d.s("notes"),
                    "tags" to d.s("tags"),
                    "extra" to json.encodeToString(ListSerializer(ExtraField.serializer()), extra),
                    "favorite" to d.b("favorite"),
                    "created_at" to d.s("created_at", now),
                    "updated_at" to d.s("updated_at", now),
                ))
            }
            "todo" -> {
                val done = d.b("done")
                // 父任务可能在本批稍后才写入，先记下，全部写完后再补上
                parents[item.id] = d.sOrNull("parent")
                val now = nowLocal()
                upsert(kind, item, mapOf(
                    "project_id" to idOf("project", d.sOrNull("project")),
                    "parent_id" to idOf("todo", d.sOrNull("parent")),
                    "title" to d.s("title", "未命名"),
                    "notes" to d.s("notes"),
                    "priority" to (idOf("priority", d.sOrNull("priority")) ?: defaultPriority()),
                    "status_id" to (idOf("status", d.sOrNull("status")) ?: firstStatus(done)),
                    "done" to done,
                    "done_at" to if (done) d.sOrNull("done_at") else null,
                    "start_date" to d.s("start_date"),
                    "due_date" to d.s("due_date"),
                    "remind_at" to d.s("remind_at"),
                    "tags" to d.s("tags"),
                    "repeat" to d.s("repeat"),
                    "created_at" to d.s("created_at", now),
                    "updated_at" to d.s("updated_at", now),
                ), listOf("reminded = CASE WHEN remind_at <> excluded.remind_at THEN 0 ELSE reminded END"))
            }
            "comment" -> {
                val todoId = idOf("todo", d.sOrNull("todo")) ?: return // 所属待办已在本机删除
                upsert(kind, item, mapOf(
                    "todo_id" to todoId, "content" to d.s("content"), "created_at" to d.s("created_at", nowLocal()),
                ))
            }
        }
    }

    // ---------- 拉取与合并 ----------

    private class Decoded(val item: RemoteItem, val kind: String, val data: JsonObject?)

    /** 合并云端变更，返回是否改动了本地数据 */
    private fun applyRemote(items: List<Decoded>, cursor: Long): Boolean {
        var changed = false
        db.tx {
            db.exec("INSERT INTO sync_apply (x) VALUES (1)")
            try {
                val parents = LinkedHashMap<String, String?>()
                val deletes = mutableListOf<Decoded>()
                val sorted = items.sortedWith(compareBy<Decoded> { ORDER.indexOf(it.kind) }.thenBy { it.item.revision })
                for (x in sorted) {
                    val item = x.item
                    val table = TABLE[x.kind]!!
                    val local = db.one("SELECT id, sync_rev, sync_dirty, sync_mtime FROM $table WHERE uid = ?", item.id)
                    val tomb = db.one("SELECT rev, mtime FROM sync_tombstones WHERE kind = ? AND uid = ?", x.kind, item.id)

                    if (local != null && local.long("sync_rev") >= item.revision) continue // 本机已是该版本

                    // 删除优先：一端删除、另一端同时修改时保留删除
                    if (tomb != null) {
                        if (item.deleted) db.exec("DELETE FROM sync_tombstones WHERE kind = ? AND uid = ?", x.kind, item.id)
                        else db.exec("UPDATE sync_tombstones SET rev = ? WHERE kind = ? AND uid = ?", item.revision, x.kind, item.id)
                        continue
                    }
                    if (!item.deleted && local != null && local.bool("sync_dirty") && local.long("sync_mtime") > item.modifiedAt) {
                        // 本机修改更晚：保留本机内容，记下云端版本号以便推送
                        db.exec("UPDATE $table SET sync_rev = ? WHERE id = ?", item.revision, local.long("id"))
                        continue
                    }
                    if (item.deleted) {
                        if (local != null) deletes.add(x)
                        continue
                    }
                    applyUpsert(x.kind, item, x.data ?: JsonObject(emptyMap()), parents)
                    changed = true
                }

                // 补上父子关系
                for ((uid, parentUid) in parents) {
                    if (parentUid != null) db.exec("UPDATE todos SET parent_id = ? WHERE uid = ?", idOf("todo", parentUid), uid)
                }

                for (x in deletes.sortedByDescending { ORDER.indexOf(it.kind) }) {
                    db.exec("DELETE FROM ${TABLE[x.kind]} WHERE uid = ?", x.item.id)
                    changed = true
                }

                repo.setSetting(Key.CURSOR, cursor.toString())
            } finally {
                db.exec("DELETE FROM sync_apply")
            }

            // 云端删除了本机待办仍在使用的优先级 / 状态时，改用默认值（作为本机修改同步出去）
            defaultPriority()?.let { db.exec("UPDATE todos SET priority = ? WHERE priority NOT IN (SELECT id FROM priorities)", it) }
            for (done in listOf(false, true)) {
                firstStatus(done)?.let {
                    db.exec("UPDATE todos SET status_id = ? WHERE done = ? AND status_id NOT IN (SELECT id FROM statuses)", it, done)
                }
            }
        }
        return changed
    }

    private suspend fun pull(key: ByteArray, account: String): Boolean {
        var since = db.io { repo.getSetting(Key.CURSOR)?.toLongOrNull() ?: 0 }
        val items = mutableListOf<RemoteItem>()
        while (true) {
            val page = json.decodeFromString(PullResponse.serializer(), cloud.request("/api/sync/pull?since=$since&limit=$PAGE"))
            items.addAll(page.items)
            since = page.revision
            if (!page.hasMore) break
        }
        if (cloud.currentAccount() != account) throw CloudException("账号已变更，同步已取消")

        val decoded = items.filter { isKind(it.kind) }.map { item ->
            val data = if (item.deleted) null else try {
                json.parseToJsonElement(E2ee.decryptRecord(key, item.kind, item.id, item.payload ?: "")) as? JsonObject
            } catch (e: Exception) {
                throw CloudException("无法解密云端数据：数据密钥不匹配或数据已损坏，请重新登录")
            }
            Decoded(item, item.kind, data)
        }
        val cursor = since
        return db.io { applyRemote(decoded, cursor) }
    }

    // ---------- 推送 ----------

    private fun collect(key: ByteArray, limit: Int): List<Outgoing> {
        val out = mutableListOf<Outgoing>()
        for (t in Schema.SYNC_TABLES) {
            if (out.size >= limit) break
            val rows = db.query("SELECT * FROM ${t.table} WHERE sync_dirty = 1 AND uid IS NOT NULL ORDER BY id LIMIT ?", limit - out.size)
            for (r in rows) {
                val uid = r.str("uid")
                val payload = json.encodeToString(JsonObject.serializer(), serialize(t.kind, r))
                out.add(Outgoing(t.kind, uid, r.long("sync_rev"), false, r.long("sync_mtime"), E2ee.encryptRecord(key, t.kind, uid, payload)))
            }
        }
        if (out.size < limit) {
            for (t in db.query("SELECT kind, uid, rev, mtime FROM sync_tombstones ORDER BY mtime LIMIT ?", limit - out.size)) {
                if (isKind(t.str("kind"))) out.add(Outgoing(t.str("kind"), t.str("uid"), t.long("rev"), true, t.long("mtime"), null))
            }
        }
        return out
    }

    /** 推送本机修改，返回是否存在冲突（需要重新拉取） */
    private suspend fun push(key: ByteArray, account: String): Boolean {
        while (true) {
            val changes = db.io { collect(key, PAGE) }
            if (changes.isEmpty()) return false
            val res = json.decodeFromString(
                PushResponse.serializer(),
                cloud.request("/api/sync/push", HttpMethod.Post, json.encodeToString(PushRequest.serializer(), PushRequest(changes))),
            )
            if (cloud.currentAccount() != account) throw CloudException("账号已变更，同步已取消")

            val sent = changes.associateBy { "${it.kind}/${it.id}" }
            db.io {
                db.tx {
                    for (a in res.accepted) {
                        val c = sent["${a.kind}/${a.id}"] ?: continue
                        if (c.deleted) {
                            db.exec("DELETE FROM sync_tombstones WHERE kind = ? AND uid = ? AND mtime = ?", c.kind, c.id, c.modifiedAt)
                        } else {
                            // 推送期间又被修改的记录保持待同步
                            db.exec(
                                "UPDATE ${TABLE[c.kind]} SET sync_rev = ?, sync_dirty = CASE WHEN sync_mtime = ? THEN 0 ELSE 1 END WHERE uid = ?",
                                a.revision, c.modifiedAt, c.id,
                            )
                        }
                    }
                }
            }
            if (res.conflicts.isNotEmpty()) {
                db.io { resolveConflicts(res.conflicts) }
                return true
            }
            if (changes.size < PAGE) return false
        }
    }

    /** 冲突记录需要拉取云端版本后再合并；游标异常时回退，确保能拉到这些记录 */
    private fun resolveConflicts(conflicts: List<ItemRef>) = db.tx {
        var cursor = repo.getSetting(Key.CURSOR)?.toLongOrNull() ?: 0
        for (c in conflicts) {
            if (!isKind(c.kind)) continue
            if (c.revision == 0L) {
                // 云端没有这条记录：修改按新记录重新上传，删除则无需再推送
                db.exec("UPDATE ${TABLE[c.kind]} SET sync_rev = 0 WHERE uid = ?", c.id)
                db.exec("DELETE FROM sync_tombstones WHERE kind = ? AND uid = ?", c.kind, c.id)
            } else {
                cursor = minOf(cursor, c.revision - 1)
            }
        }
        repo.setSetting(Key.CURSOR, cursor.toString())
    }

    // ---------- 账号绑定与调度 ----------

    /** 切换到另一个账号（或服务器）时，本机所有数据视为未同步，登录后整体上传并与云端合并 */
    private fun bindAccount(account: String) {
        if (repo.getSetting(Key.ACCOUNT) == account) return
        db.tx {
            for (t in Schema.SYNC_TABLES) db.exec("UPDATE ${t.table} SET sync_rev = 0, sync_dirty = 1")
            db.exec("DELETE FROM sync_tombstones WHERE rev > 0")
            repo.setSetting(Key.CURSOR, "0")
            repo.setSetting(Key.ACCOUNT, account)
        }
    }

    fun pendingCount(): Int {
        val parts = Schema.SYNC_TABLES.map { "(SELECT COUNT(*) FROM ${it.table} WHERE sync_dirty = 1)" }
        return (db.long("SELECT ${parts.joinToString(" + ")} + (SELECT COUNT(*) FROM sync_tombstones)") ?: 0).toInt()
    }

    private suspend fun runOnce() {
        val account = cloud.currentAccount() ?: return
        val key = db.io { cloud.vaultKey() } ?: return
        db.io { bindAccount(account) }
        cloud.setSyncStatus { it.copy(syncing = true) }
        var changed = false
        try {
            for (round in 0 until MAX_ROUNDS) {
                changed = pull(key, account) || changed
                if (!push(key, account)) break
                if (round == MAX_ROUNDS - 1) throw CloudException("存在持续同步冲突，请稍后重试")
            }
            cloud.setSyncStatus { it.copy(lastSyncAt = Dates.nowIso(), error = null) }
        } catch (e: Exception) {
            if (cloud.currentAccount() != null) cloud.setSyncStatus { it.copy(error = e.message ?: "同步失败") }
        } finally {
            if (changed) onRemoteChange()
            if (cloud.currentAccount() != null) {
                val pending = db.io { pendingCount() }
                cloud.setSyncStatus { it.copy(syncing = false, pending = pending) }
            }
        }
    }

    /** 立即同步；正在同步时等待当前这轮结束后再补一轮 */
    suspend fun syncNow() {
        if (mutex.isLocked) {
            rerun = true
            mutex.withLock { }
            return
        }
        mutex.withLock { runOnce() }
        if (rerun) {
            rerun = false
            syncNow()
        }
    }

    /** 本地数据变化后调用：合并短时间内的多次修改，稍后再同步 */
    fun schedule(delayMs: Long = DEBOUNCE_MS) {
        if (cloud.currentAccount() == null) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    /** 本机待同步数量（数据变化后刷新） */
    fun refreshPending() {
        if (cloud.currentAccount() == null) return
        scope.launch {
            val n = db.io { pendingCount() }
            cloud.setSyncStatus { it.copy(pending = n) }
        }
    }

    fun start() {
        if (intervalJob == null) {
            intervalJob = scope.launch {
                while (isActive) {
                    delay(INTERVAL_MS)
                    syncNow()
                }
            }
        }
        refreshPending()
        schedule(1_000)
    }

    fun stop() {
        intervalJob?.cancel()
        debounceJob?.cancel()
        intervalJob = null
        debounceJob = null
    }

    /** 应用回到前台时立即同步一次（手机上进程常被挂起，定时器不可靠） */
    fun onResume() {
        if (cloud.currentAccount() != null) schedule(300)
    }
}

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
