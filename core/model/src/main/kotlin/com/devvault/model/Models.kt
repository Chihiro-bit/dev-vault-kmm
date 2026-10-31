package com.devvault.model

import kotlinx.serialization.Serializable

/** 与桌面端 src/shared/types.ts 保持一致 */
enum class Category(val key: String) {
    ACCOUNT("account"), GIT("git"), VPN("vpn"), SERVER("server"), DATABASE("database"), OTHER("other");

    companion object {
        fun of(key: String?): Category = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** 项目范围：全部 / 未分组 / 指定项目 */
sealed interface Scope {
    data object All : Scope
    data object None : Scope
    data class Project(val id: Long) : Scope
}

data class Project(
    val id: Long,
    val name: String,
    val color: String,
    val description: String,
    val createdAt: String,
    val entryCount: Int,
    val todoCount: Int,
)

@Serializable
data class ExtraField(val key: String = "", val value: String = "", val secret: Boolean = false)

data class Entry(
    val id: Long = 0,
    val projectId: Long? = null,
    val projectName: String? = null,
    val projectColor: String? = null,
    val category: Category = Category.ACCOUNT,
    val title: String = "",
    val env: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val verifyCode: String = "",
    val totp: String = "",
    /** YYYY-MM-DD，空表示长期有效 */
    val expiresAt: String = "",
    val notes: String = "",
    val tags: String = "",
    val extra: List<ExtraField> = emptyList(),
    val favorite: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
)

data class PriorityLevel(
    val id: Long,
    val label: String,
    val name: String,
    val color: String,
    val sortOrder: Int,
    val isDefault: Boolean,
    val todoCount: Int,
)

enum class StatusKind(val key: String) {
    TODO("todo"), ACTIVE("active"), DONE("done");

    companion object {
        fun of(key: String?): StatusKind = entries.firstOrNull { it.key == key } ?: TODO
    }
}

data class TodoStatus(
    val id: Long,
    val name: String,
    val color: String,
    val kind: StatusKind,
    val sortOrder: Int,
    val todoCount: Int,
)

enum class Repeat(val key: String, val label: String) {
    NONE("", "不重复"), DAILY("daily", "每天"), WEEKDAYS("weekdays", "每个工作日"),
    WEEKLY("weekly", "每周"), MONTHLY("monthly", "每月");

    companion object {
        fun of(key: String?): Repeat = entries.firstOrNull { it.key == key } ?: NONE
    }
}

data class Todo(
    val id: Long,
    val projectId: Long?,
    val projectName: String?,
    val projectColor: String?,
    val parentId: Long?,
    val parentTitle: String?,
    val title: String,
    val notes: String,
    val priority: Long,
    val priorityLabel: String?,
    val priorityName: String?,
    val priorityColor: String?,
    val statusId: Long,
    val statusName: String?,
    val statusColor: String?,
    val statusKind: StatusKind,
    val startDate: String,
    val dueDate: String,
    /** YYYY-MM-DDTHH:mm，空表示不提醒 */
    val remindAt: String,
    val tags: String,
    val repeat: Repeat,
    val subtaskTotal: Int,
    val subtaskDone: Int,
    val commentCount: Int,
    val done: Boolean,
    val doneAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

data class TodoInput(
    val projectId: Long?,
    val parentId: Long? = null,
    val title: String,
    val notes: String = "",
    val priority: Long? = null,
    val statusId: Long? = null,
    val startDate: String = "",
    val dueDate: String = "",
    val remindAt: String = "",
    val tags: String = "",
    val repeat: Repeat = Repeat.NONE,
)

/** 详情页逐字段修改，null 表示不修改 */
data class TodoPatch(
    val title: String? = null,
    val notes: String? = null,
    val priority: Long? = null,
    val statusId: Long? = null,
    /** 使用 [ProjectChange] 区分「不修改」与「改为未分组」 */
    val project: ProjectChange? = null,
    val startDate: String? = null,
    val dueDate: String? = null,
    val remindAt: String? = null,
    val tags: String? = null,
    val repeat: Repeat? = null,
)

data class ProjectChange(val projectId: Long?)

data class TodoComment(val id: Long, val todoId: Long, val content: String, val createdAt: String)

data class Stats(val entries: Int, val todos: Int, val noneEntries: Int, val noneTodos: Int)

@Serializable
data class CloudUser(val id: String, val name: String, val username: String, val email: String? = null)

data class SyncStatus(
    val syncing: Boolean = false,
    val lastSyncAt: String? = null,
    val error: String? = null,
    val pending: Int = 0,
)

data class CloudState(
    val serverUrl: String = "",
    val user: CloudUser? = null,
    val loggedIn: Boolean = false,
    val sync: SyncStatus = SyncStatus(),
)

@Serializable
data class ServerInfo(
    val name: String = "DevVault",
    val version: String = "",
    val apiVersion: Int = 0,
    val allowRegistration: Boolean = false,
)
