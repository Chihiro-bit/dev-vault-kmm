package com.devvault.data

import com.devvault.crypto.LocalCipher
import com.devvault.model.Category
import com.devvault.model.Entry
import com.devvault.model.ExtraField
import com.devvault.model.PriorityLevel
import com.devvault.model.Project
import com.devvault.model.Repeat
import com.devvault.model.Scope
import com.devvault.model.Stats
import com.devvault.model.StatusKind
import com.devvault.model.Todo
import com.devvault.model.TodoComment
import com.devvault.model.TodoInput
import com.devvault.model.TodoPatch
import com.devvault.model.TodoStatus
import com.devvault.util.Dates
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 本地数据访问，逻辑移植自桌面端 src/main/repo.ts。
 * 方法均为同步调用，调用方需在 [Db.io] 中执行。
 */
class Repository(val db: Db) {
    private val json = Json { ignoreUnknownKeys = true }
    private val NOW = "datetime('now','localtime')"

    // ---------- 设置 ----------

    fun getSetting(key: String): String? = db.one("SELECT value FROM settings WHERE key = ?", key)?.strOrNull("value")

    fun setSetting(key: String, value: String?) {
        if (value == null) db.exec("DELETE FROM settings WHERE key = ?", key)
        else db.exec("INSERT INTO settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value)
    }

    // ---------- 工具 ----------

    private fun scopeClause(scope: Scope, col: String, params: MutableList<Any?>): List<String> = when (scope) {
        Scope.All -> emptyList()
        Scope.None -> listOf("$col IS NULL")
        is Scope.Project -> { params.add(scope.id); listOf("$col = ?") }
    }

    private fun where(clauses: List<String>) = if (clauses.isEmpty()) "" else "WHERE " + clauses.joinToString(" AND ")

    /** 空格分隔多个关键词，全部命中才算匹配 */
    private fun matches(keyword: String?, vararg fields: String?): Boolean {
        val words = keyword?.trim()?.lowercase()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: return true
        if (words.isEmpty()) return true
        val hay = fields.filterNotNull().joinToString("\n").lowercase()
        return words.all { it in hay }
    }

    private fun normalizeTags(tags: String): String =
        tags.split(Regex("[,，\\s]+")).map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinct().joinToString(",")

    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val REMIND_RE = Regex("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2})?$")
    private fun date(s: String?) = if (s != null && DATE_RE.matches(s)) s else ""

    // ---------- 项目 ----------

    fun listProjects(): List<Project> = db.query(
        """SELECT p.*,
             (SELECT COUNT(*) FROM entries e WHERE e.project_id = p.id) AS entry_count,
             (SELECT COUNT(*) FROM todos t WHERE t.project_id = p.id AND t.done = 0 AND t.parent_id IS NULL) AS todo_count
           FROM projects p ORDER BY p.sort_order, p.id""",
    ).map {
        Project(it.long("id"), it.str("name"), it.str("color"), it.str("description"), it.str("created_at"),
            it.int("entry_count"), it.int("todo_count"))
    }

    fun saveProject(id: Long?, name: String, color: String, description: String): Long {
        val n = name.trim()
        if (n.isEmpty()) throw IllegalArgumentException("项目名称不能为空")
        return if (id != null && id > 0) {
            db.exec("UPDATE projects SET name = ?, color = ?, description = ? WHERE id = ?", n, color, description.trim(), id)
            id
        } else {
            val order = db.long("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM projects") ?: 1
            db.insert("INSERT INTO projects (name, color, description, sort_order) VALUES (?, ?, ?, ?)", n, color, description.trim(), order)
        }
    }

    fun deleteProject(id: Long) = db.exec("DELETE FROM projects WHERE id = ?", id)

    fun stats(): Stats {
        val r = db.one(
            """SELECT (SELECT COUNT(*) FROM entries) AS entries,
                 (SELECT COUNT(*) FROM todos WHERE done = 0 AND parent_id IS NULL) AS todos,
                 (SELECT COUNT(*) FROM entries WHERE project_id IS NULL) AS none_entries,
                 (SELECT COUNT(*) FROM todos WHERE done = 0 AND parent_id IS NULL AND project_id IS NULL) AS none_todos""",
        )!!
        return Stats(r.int("entries"), r.int("todos"), r.int("none_entries"), r.int("none_todos"))
    }

    // ---------- 账号密码 ----------

    private fun rowToEntry(r: Row): Entry {
        val extra = try {
            json.decodeFromString(ListSerializer(ExtraField.serializer()), r.str("extra").ifEmpty { "[]" })
                .map { if (it.secret) it.copy(value = LocalCipher.decrypt(it.value)) else it }
        } catch (e: Exception) {
            emptyList()
        }
        return Entry(
            id = r.long("id"),
            projectId = r.longOrNull("project_id"),
            projectName = r.strOrNull("project_name"),
            projectColor = r.strOrNull("project_color"),
            category = Category.of(r.str("category")),
            title = r.str("title"),
            env = r.str("env"),
            url = r.str("url"),
            username = r.str("username"),
            password = LocalCipher.decrypt(r.str("password")),
            verifyCode = r.str("verify_code"),
            totp = LocalCipher.decrypt(r.str("totp")),
            expiresAt = r.str("expires_at"),
            notes = r.str("notes"),
            tags = r.str("tags"),
            extra = extra,
            favorite = r.bool("favorite"),
            createdAt = r.str("created_at"),
            updatedAt = r.str("updated_at"),
        )
    }

    fun listEntries(scope: Scope, keyword: String? = null): List<Entry> {
        val params = mutableListOf<Any?>()
        val clauses = scopeClause(scope, "e.project_id", params)
        return db.query(
            """SELECT e.*, p.name AS project_name, p.color AS project_color
               FROM entries e LEFT JOIN projects p ON p.id = e.project_id
               ${where(clauses)}
               ORDER BY e.favorite DESC, e.updated_at DESC, e.id DESC""",
            *params.toTypedArray(),
        ).map(::rowToEntry).filter {
            matches(keyword, it.title, it.username, it.url, it.verifyCode, it.notes, it.tags, it.env, it.projectName,
                it.extra.filter { f -> !f.secret }.joinToString(" ") { f -> "${f.key} ${f.value}" })
        }
    }

    fun getEntry(id: Long): Entry? = db.one(
        """SELECT e.*, p.name AS project_name, p.color AS project_color
           FROM entries e LEFT JOIN projects p ON p.id = e.project_id WHERE e.id = ?""",
        id,
    )?.let(::rowToEntry)

    fun saveEntry(e: Entry): Long {
        val title = e.title.trim()
        if (title.isEmpty()) throw IllegalArgumentException("名称不能为空")
        val extra = json.encodeToString(
            ListSerializer(ExtraField.serializer()),
            e.extra.filter { it.key.isNotBlank() || it.value.isNotBlank() }
                .map { it.copy(key = it.key.trim(), value = if (it.secret) LocalCipher.encrypt(it.value) else it.value) },
        )
        val args = arrayOf<Any?>(
            e.projectId, e.category.key, title, e.env, e.url.trim(), e.username.trim(), LocalCipher.encrypt(e.password),
            e.verifyCode.trim(), LocalCipher.encrypt(e.totp.trim()), date(e.expiresAt), e.notes, normalizeTags(e.tags),
            extra, e.favorite,
        )
        return if (e.id > 0) {
            db.exec(
                """UPDATE entries SET project_id=?, category=?, title=?, env=?, url=?, username=?, password=?, verify_code=?,
                     totp=?, expires_at=?, notes=?, tags=?, extra=?, favorite=?, updated_at=$NOW WHERE id=?""",
                *args, e.id,
            )
            e.id
        } else {
            db.insert(
                """INSERT INTO entries (project_id, category, title, env, url, username, password, verify_code, totp,
                     expires_at, notes, tags, extra, favorite) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                *args,
            )
        }
    }

    fun deleteEntry(id: Long) = db.exec("DELETE FROM entries WHERE id = ?", id)

    fun toggleFavorite(id: Long) = db.exec("UPDATE entries SET favorite = 1 - favorite WHERE id = ?", id)

    // ---------- 待办 ----------

    private val TODO_SELECT = """SELECT t.id, t.project_id, t.parent_id, t.title, t.notes, t.priority, t.status_id,
        t.start_date, t.due_date, t.remind_at, t.tags, t.repeat, t.done, t.done_at, t.created_at, t.updated_at,
        p.name AS project_name, p.color AS project_color,
        pr.label AS priority_label, pr.name AS priority_name, pr.color AS priority_color,
        st.name AS status_name, st.color AS status_color,
        COALESCE(st.kind, CASE WHEN t.done = 1 THEN 'done' ELSE 'todo' END) AS status_kind,
        par.title AS parent_title,
        (SELECT COUNT(*) FROM todos c WHERE c.parent_id = t.id) AS subtask_total,
        (SELECT COUNT(*) FROM todos c WHERE c.parent_id = t.id AND c.done = 1) AS subtask_done,
        (SELECT COUNT(*) FROM todo_comments m WHERE m.todo_id = t.id) AS comment_count
      FROM todos t
      LEFT JOIN projects p ON p.id = t.project_id
      LEFT JOIN priorities pr ON pr.id = t.priority
      LEFT JOIN statuses st ON st.id = t.status_id
      LEFT JOIN todos par ON par.id = t.parent_id"""

    /** 按等级顺序排序，未定级（等级已不存在）的排最后 */
    private val TODO_ORDER = "t.done ASC, COALESCE(pr.sort_order, 1000000) ASC, (t.due_date = '') ASC, t.due_date ASC, t.id DESC"

    private fun rowToTodo(r: Row) = Todo(
        id = r.long("id"),
        projectId = r.longOrNull("project_id"),
        projectName = r.strOrNull("project_name"),
        projectColor = r.strOrNull("project_color"),
        parentId = r.longOrNull("parent_id"),
        parentTitle = r.strOrNull("parent_title"),
        title = r.str("title"),
        notes = r.str("notes"),
        priority = r.long("priority"),
        priorityLabel = r.strOrNull("priority_label"),
        priorityName = r.strOrNull("priority_name"),
        priorityColor = r.strOrNull("priority_color"),
        statusId = r.long("status_id"),
        statusName = r.strOrNull("status_name"),
        statusColor = r.strOrNull("status_color"),
        statusKind = StatusKind.of(r.str("status_kind")),
        startDate = r.str("start_date"),
        dueDate = r.str("due_date"),
        remindAt = r.str("remind_at"),
        tags = r.str("tags"),
        repeat = Repeat.of(r.str("repeat")),
        subtaskTotal = r.int("subtask_total"),
        subtaskDone = r.int("subtask_done"),
        commentCount = r.int("comment_count"),
        done = r.bool("done"),
        doneAt = r.strOrNull("done_at"),
        createdAt = r.str("created_at"),
        updatedAt = r.str("updated_at"),
    )

    /** 范围内的全部待办（含子任务），由界面决定是否只展示顶层任务 */
    fun listTodos(scope: Scope, keyword: String? = null): List<Todo> {
        val params = mutableListOf<Any?>()
        val clauses = scopeClause(scope, "t.project_id", params)
        return db.query("$TODO_SELECT ${where(clauses)} ORDER BY $TODO_ORDER", *params.toTypedArray())
            .map(::rowToTodo)
            .filter { matches(keyword, it.title, it.notes, it.tags, it.projectName) }
    }

    fun listSubtasks(parentId: Long): List<Todo> =
        db.query("$TODO_SELECT WHERE t.parent_id = ? ORDER BY t.done ASC, t.id ASC", parentId).map(::rowToTodo)

    fun getTodo(id: Long): Todo? = db.one("$TODO_SELECT WHERE t.id = ?", id)?.let(::rowToTodo)

    private fun statusKindOf(id: Long?): StatusKind? =
        id?.let { db.one("SELECT kind FROM statuses WHERE id = ?", it)?.let { r -> StatusKind.of(r.str("kind")) } }

    /** 按顺序取第一个属于给定分类的状态 */
    private fun firstStatus(vararg kinds: StatusKind): Long {
        for (k in kinds) {
            db.long("SELECT id FROM statuses WHERE kind = ? ORDER BY sort_order, id LIMIT 1", k.key)?.let { return it }
        }
        throw IllegalStateException("没有可用的状态")
    }

    fun defaultPriorityId(): Long =
        db.long("SELECT id FROM priorities ORDER BY is_default DESC, sort_order, id LIMIT 1") ?: 0

    private fun priorityExists(id: Long?) = id != null && db.long("SELECT 1 FROM priorities WHERE id = ?", id) != null

    /** 新建待办。子任务沿用父任务的项目 */
    fun createTodo(t: TodoInput): Long {
        val title = t.title.trim()
        if (title.isEmpty()) throw IllegalArgumentException("待办内容不能为空")
        val parent = t.parentId?.let { db.one("SELECT id, project_id FROM todos WHERE id = ?", it) }
        val statusId = t.statusId?.takeIf { statusKindOf(it) != null } ?: firstStatus(StatusKind.TODO, StatusKind.ACTIVE)
        val done = statusKindOf(statusId) == StatusKind.DONE
        return db.insert(
            """INSERT INTO todos (project_id, parent_id, title, notes, priority, status_id, done, done_at, start_date,
                 due_date, remind_at, tags, repeat)
               VALUES (?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 1 THEN $NOW ELSE NULL END, ?, ?, ?, ?, ?)""",
            if (parent != null) parent.longOrNull("project_id") else t.projectId,
            parent?.long("id"),
            title,
            t.notes,
            if (priorityExists(t.priority)) t.priority else defaultPriorityId(),
            statusId,
            done,
            done,
            date(t.startDate),
            date(t.dueDate),
            if (REMIND_RE.matches(t.remindAt)) t.remindAt else "",
            normalizeTags(t.tags),
            t.repeat.key,
        )
    }

    /** 修改状态并同步完成标记；重复任务完成时自动生成下一次 */
    private fun setStatus(id: Long, statusId: Long) {
        val kind = statusKindOf(statusId) ?: throw IllegalArgumentException("状态不存在")
        val cur = db.one("SELECT done FROM todos WHERE id = ?", id) ?: return
        val done = kind == StatusKind.DONE
        db.exec(
            """UPDATE todos SET status_id = ?, done = ?,
                 done_at = CASE WHEN ? = 1 THEN COALESCE(done_at, $NOW) ELSE NULL END,
                 updated_at = $NOW
               WHERE id = ?""",
            statusId, done, done, id,
        )
        if (done && !cur.bool("done")) spawnNextOccurrence(id)
    }

    /** 勾选 / 取消勾选：在第一个「已完成」与第一个「未开始」状态之间切换 */
    fun toggleTodo(id: Long) = db.tx {
        val cur = db.one("SELECT done FROM todos WHERE id = ?", id) ?: return@tx
        setStatus(id, if (cur.bool("done")) firstStatus(StatusKind.TODO, StatusKind.ACTIVE) else firstStatus(StatusKind.DONE))
    }

    fun patchTodo(id: Long, patch: TodoPatch) = db.tx {
        val sets = mutableListOf<String>()
        val args = mutableListOf<Any?>()
        fun set(col: String, v: Any?) { sets.add("$col = ?"); args.add(v) }
        patch.title?.let {
            if (it.isBlank()) throw IllegalArgumentException("待办内容不能为空")
            set("title", it.trim())
        }
        patch.notes?.let { set("notes", it) }
        patch.priority?.let { set("priority", if (priorityExists(it)) it else defaultPriorityId()) }
        patch.startDate?.let { set("start_date", date(it)) }
        patch.dueDate?.let { set("due_date", date(it)) }
        patch.remindAt?.let {
            set("remind_at", if (REMIND_RE.matches(it)) it else "")
            set("reminded", 0)
        }
        patch.tags?.let { set("tags", normalizeTags(it)) }
        patch.repeat?.let { set("repeat", it.key) }
        patch.project?.let {
            set("project_id", it.projectId)
            // 子任务跟随父任务的项目
            db.exec("UPDATE todos SET project_id = ? WHERE parent_id = ?", it.projectId, id)
        }
        if (sets.isNotEmpty()) {
            db.exec("UPDATE todos SET ${sets.joinToString(", ")}, updated_at = $NOW WHERE id = ?", *args.toTypedArray(), id)
        }
        patch.statusId?.let { setStatus(id, it) }
    }

    private fun spawnNextOccurrence(id: Long) {
        val t = db.one("SELECT * FROM todos WHERE id = ?", id) ?: return
        val repeat = Repeat.of(t.str("repeat"))
        if (repeat == Repeat.NONE) return
        val oldDue = t.str("due_date")
        val due = Dates.nextDueDate(oldDue, repeat)
        var start = ""
        val oldStart = t.str("start_date")
        if (oldStart.isNotEmpty()) {
            val gap = if (oldDue.isNotEmpty()) Dates.daysBetween(oldStart, oldDue) else 0
            start = Dates.addDays(due, -maxOf(0, gap))
        }
        // 提醒时间保留原来的时刻，日期对齐到新的截止日期
        val remind = t.str("remind_at").let { if (it.length >= 16) "${due}T${it.substring(11, 16)}" else "" }
        val newId = createTodo(
            TodoInput(
                projectId = t.longOrNull("project_id"),
                parentId = t.longOrNull("parent_id"),
                title = t.str("title"),
                notes = t.str("notes"),
                priority = t.long("priority"),
                startDate = start,
                dueDate = due,
                remindAt = remind,
                tags = t.str("tags"),
                repeat = repeat,
            ),
        )
        // 子任务作为检查清单一并复制（重置为未完成）
        for (c in db.query("SELECT title, notes, priority, tags FROM todos WHERE parent_id = ? ORDER BY id", id)) {
            createTodo(
                TodoInput(projectId = t.longOrNull("project_id"), parentId = newId, title = c.str("title"),
                    notes = c.str("notes"), priority = c.long("priority"), tags = c.str("tags")),
            )
        }
        db.exec("UPDATE todos SET repeat = '' WHERE id = ?", id)
    }

    fun deleteTodo(id: Long) = db.exec("DELETE FROM todos WHERE id = ?", id)

    /** 清理已完成的顶层任务（仍有未完成子任务的除外） */
    fun clearDoneTodos(scope: Scope): Long {
        val params = mutableListOf<Any?>()
        val clauses = listOf(
            "done = 1", "parent_id IS NULL",
            "NOT EXISTS (SELECT 1 FROM todos c WHERE c.parent_id = todos.id AND c.done = 0)",
        ) + scopeClause(scope, "project_id", params)
        db.exec("DELETE FROM todos ${where(clauses)}", *params.toTypedArray())
        return db.changes()
    }

    // ---------- 进展记录 ----------

    fun listComments(todoId: Long): List<TodoComment> =
        db.query("SELECT * FROM todo_comments WHERE todo_id = ? ORDER BY id", todoId)
            .map { TodoComment(it.long("id"), it.long("todo_id"), it.str("content"), it.str("created_at")) }

    fun addComment(todoId: Long, content: String): Long {
        val c = content.trim()
        if (c.isEmpty()) throw IllegalArgumentException("内容不能为空")
        return db.insert("INSERT INTO todo_comments (todo_id, content) VALUES (?, ?)", todoId, c)
    }

    fun deleteComment(id: Long) = db.exec("DELETE FROM todo_comments WHERE id = ?", id)

    // ---------- 状态与优先级 ----------

    fun listStatuses(): List<TodoStatus> = db.query(
        """SELECT s.*, (SELECT COUNT(*) FROM todos t WHERE t.status_id = s.id AND t.parent_id IS NULL) AS todo_count
           FROM statuses s ORDER BY s.sort_order, s.id""",
    ).map { TodoStatus(it.long("id"), it.str("name"), it.str("color"), StatusKind.of(it.str("kind")), it.int("sort_order"), it.int("todo_count")) }

    fun listPriorities(): List<PriorityLevel> = db.query(
        """SELECT pr.*, (SELECT COUNT(*) FROM todos t WHERE t.priority = pr.id AND t.done = 0) AS todo_count
           FROM priorities pr ORDER BY pr.sort_order, pr.id""",
    ).map {
        PriorityLevel(it.long("id"), it.str("label"), it.str("name"), it.str("color"), it.int("sort_order"),
            it.bool("is_default"), it.int("todo_count"))
    }
}
