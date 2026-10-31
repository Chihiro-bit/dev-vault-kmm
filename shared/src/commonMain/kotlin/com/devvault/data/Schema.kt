package com.devvault.data

/**
 * 数据库结构，与桌面端 src/main/db.ts 迁移到 v7 后的结构一致（去掉了仅桌面使用的列，如桌面任务条位置）。
 * 同步相关的触发器逻辑完全相同：本地修改标记 sync_dirty，删除写入 sync_tombstones。
 */
object Schema {
    const val VERSION = 1

    private const val NOW = "(datetime('now','localtime'))"
    const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"
    private const val NOT_APPLYING = "NOT EXISTS (SELECT 1 FROM sync_apply)"

    class SyncTable(val table: String, val kind: String, val cols: List<String>)

    val SYNC_TABLES = listOf(
        SyncTable("priorities", "priority", listOf("label", "name", "color", "sort_order", "is_default")),
        SyncTable("statuses", "status", listOf("name", "color", "kind", "sort_order")),
        SyncTable("projects", "project", listOf("name", "color", "description", "sort_order")),
        SyncTable(
            "entries", "entry",
            listOf(
                "project_id", "category", "title", "env", "url", "username", "password", "verify_code", "totp",
                "expires_at", "notes", "tags", "extra", "favorite",
            ),
        ),
        SyncTable(
            "todos", "todo",
            listOf(
                "project_id", "parent_id", "title", "notes", "priority", "status_id", "done", "done_at", "start_date",
                "due_date", "remind_at", "tags", "repeat",
            ),
        ),
        SyncTable("todo_comments", "comment", listOf("content")),
    )

    private const val SYNC_COLS = """,
  uid        TEXT UNIQUE,
  sync_rev   INTEGER NOT NULL DEFAULT 0,
  sync_dirty INTEGER NOT NULL DEFAULT 0,
  sync_mtime INTEGER NOT NULL DEFAULT 0"""

    private val TABLES = listOf(
        "CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)",
        "CREATE TABLE IF NOT EXISTS sync_apply (x INTEGER)",
        """CREATE TABLE IF NOT EXISTS sync_tombstones (
  kind  TEXT NOT NULL,
  uid   TEXT NOT NULL,
  rev   INTEGER NOT NULL DEFAULT 0,
  mtime INTEGER NOT NULL,
  PRIMARY KEY (kind, uid)
)""",
        """CREATE TABLE IF NOT EXISTS priorities (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  label      TEXT NOT NULL,
  name       TEXT NOT NULL DEFAULT '',
  color      TEXT NOT NULL DEFAULT '#3b6cff',
  sort_order INTEGER NOT NULL DEFAULT 0,
  is_default INTEGER NOT NULL DEFAULT 0$SYNC_COLS
)""",
        """CREATE TABLE IF NOT EXISTS statuses (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  name       TEXT NOT NULL,
  color      TEXT NOT NULL DEFAULT '#8a919e',
  kind       TEXT NOT NULL DEFAULT 'todo',
  sort_order INTEGER NOT NULL DEFAULT 0$SYNC_COLS
)""",
        """CREATE TABLE IF NOT EXISTS projects (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  name        TEXT NOT NULL,
  color       TEXT NOT NULL DEFAULT '#3b6cff',
  description TEXT NOT NULL DEFAULT '',
  sort_order  INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT $NOW$SYNC_COLS
)""",
        """CREATE TABLE IF NOT EXISTS entries (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id  INTEGER REFERENCES projects(id) ON DELETE SET NULL,
  category    TEXT NOT NULL DEFAULT 'account',
  title       TEXT NOT NULL,
  env         TEXT NOT NULL DEFAULT '',
  url         TEXT NOT NULL DEFAULT '',
  username    TEXT NOT NULL DEFAULT '',
  password    TEXT NOT NULL DEFAULT '',
  verify_code TEXT NOT NULL DEFAULT '',
  totp        TEXT NOT NULL DEFAULT '',
  expires_at  TEXT NOT NULL DEFAULT '',
  notes       TEXT NOT NULL DEFAULT '',
  tags        TEXT NOT NULL DEFAULT '',
  extra       TEXT NOT NULL DEFAULT '[]',
  favorite    INTEGER NOT NULL DEFAULT 0,
  created_at  TEXT NOT NULL DEFAULT $NOW,
  updated_at  TEXT NOT NULL DEFAULT $NOW$SYNC_COLS
)""",
        "CREATE INDEX IF NOT EXISTS idx_entries_project ON entries(project_id)",
        """CREATE TABLE IF NOT EXISTS todos (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  project_id  INTEGER REFERENCES projects(id) ON DELETE SET NULL,
  parent_id   INTEGER REFERENCES todos(id) ON DELETE CASCADE,
  title       TEXT NOT NULL,
  notes       TEXT NOT NULL DEFAULT '',
  priority    INTEGER NOT NULL DEFAULT 3,
  status_id   INTEGER,
  start_date  TEXT NOT NULL DEFAULT '',
  due_date    TEXT NOT NULL DEFAULT '',
  remind_at   TEXT NOT NULL DEFAULT '',
  reminded    INTEGER NOT NULL DEFAULT 0,
  tags        TEXT NOT NULL DEFAULT '',
  repeat      TEXT NOT NULL DEFAULT '',
  done        INTEGER NOT NULL DEFAULT 0,
  done_at     TEXT,
  created_at  TEXT NOT NULL DEFAULT $NOW,
  updated_at  TEXT NOT NULL DEFAULT $NOW$SYNC_COLS
)""",
        "CREATE INDEX IF NOT EXISTS idx_todos_project ON todos(project_id)",
        "CREATE INDEX IF NOT EXISTS idx_todos_parent ON todos(parent_id)",
        """CREATE TABLE IF NOT EXISTS todo_comments (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  todo_id    INTEGER NOT NULL REFERENCES todos(id) ON DELETE CASCADE,
  content    TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT $NOW$SYNC_COLS
)""",
        "CREATE INDEX IF NOT EXISTS idx_comments_todo ON todo_comments(todo_id)",
    )

    /**
     * 内置的默认优先级和状态，在每台设备上 id 相同，使用固定 uid，避免同步后出现重复的 P0~P3。
     * 修改时间记为 1：多台设备都未修改过时，以最先同步的设备为准（与桌面端一致）。
     * 必须在创建触发器之前写入，否则插入触发器会改写修改时间。
     */
    private val SEED = listOf(
        """INSERT INTO priorities (id, label, name, color, sort_order, is_default, uid, sync_dirty, sync_mtime) VALUES
  (1, 'P0', '紧急', '#e5484d', 0, 0, 'priority-1', 1, 1),
  (2, 'P1', '高',   '#f76b15', 1, 0, 'priority-2', 1, 1),
  (3, 'P2', '中',   '#3b6cff', 2, 1, 'priority-3', 1, 1),
  (4, 'P3', '低',   '#8a919e', 3, 0, 'priority-4', 1, 1)""",
        """INSERT INTO statuses (id, name, color, kind, sort_order, uid, sync_dirty, sync_mtime) VALUES
  (1, '待开始', '#8a919e', 'todo',   0, 'status-1', 1, 1),
  (2, '进行中', '#3b6cff', 'active', 1, 'status-2', 1, 1),
  (3, '待验证', '#e3a008', 'active', 2, 'status-3', 1, 1),
  (4, '已完成', '#16a37b', 'done',   3, 'status-4', 1, 1)""",
    )

    private fun triggers(t: SyncTable) = listOf(
        """CREATE TRIGGER IF NOT EXISTS trg_${t.table}_sync_ins AFTER INSERT ON ${t.table} WHEN $NOT_APPLYING
BEGIN
  UPDATE ${t.table} SET uid = COALESCE(uid, lower(hex(randomblob(16)))), sync_dirty = 1, sync_mtime = $NOW_MS
  WHERE id = NEW.id;
END""",
        """CREATE TRIGGER IF NOT EXISTS trg_${t.table}_sync_upd AFTER UPDATE OF ${t.cols.joinToString(", ")} ON ${t.table}
WHEN $NOT_APPLYING
BEGIN
  UPDATE ${t.table} SET sync_dirty = 1, sync_mtime = $NOW_MS WHERE id = NEW.id;
END""",
        """CREATE TRIGGER IF NOT EXISTS trg_${t.table}_sync_del AFTER DELETE ON ${t.table}
WHEN OLD.uid IS NOT NULL AND $NOT_APPLYING
  AND (OLD.sync_rev > 0 OR EXISTS (SELECT 1 FROM settings WHERE key = 'sync.account'))
BEGIN
  INSERT OR REPLACE INTO sync_tombstones (kind, uid, rev, mtime) VALUES ('${t.kind}', OLD.uid, OLD.sync_rev, $NOW_MS);
END""",
    )

    fun open(path: String): Db {
        val db = Db(path)
        db.query("PRAGMA journal_mode = WAL")
        db.exec("PRAGMA foreign_keys = ON")
        val current = db.long("PRAGMA user_version") ?: 0
        if (current < 1) {
            db.tx {
                TABLES.forEach { db.exec(it) }
                SEED.forEach { db.exec(it) }
                SYNC_TABLES.flatMap { triggers(it) }.forEach { db.exec(it) }
                db.exec("PRAGMA user_version = $VERSION")
            }
        }
        return db
    }
}
