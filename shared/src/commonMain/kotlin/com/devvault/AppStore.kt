package com.devvault

import com.devvault.data.Policy
import com.devvault.data.PolicyState
import com.devvault.data.Repository
import com.devvault.data.Schema
import com.devvault.model.Entry
import com.devvault.model.PriorityLevel
import com.devvault.model.Project
import com.devvault.model.Scope
import com.devvault.model.Stats
import com.devvault.model.Todo
import com.devvault.model.TodoStatus
import com.devvault.platform.DeviceAuth
import com.devvault.platform.Platform
import com.devvault.sync.Cloud
import com.devvault.sync.SyncEngine
import com.devvault.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 底部的轻提示（相当于桌面端的 toast） */
data class Toast(val text: String, val error: Boolean = false, val id: Long = toastSeq++)

private var toastSeq = 0L

/**
 * 全局状态：数据库、云同步与界面使用的数据快照。
 * 所有修改通过 [mutate] 执行：在数据库线程写入 → 刷新快照 → 安排同步。
 */
class AppStore private constructor() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val db = Schema.open(Platform.databasePath())
    val repo = Repository(db)
    val cloud = Cloud(db, repo)
    val policy = Policy(db, repo)
    val sync = SyncEngine(db, repo, policy, cloud, scope) { refresh() }

    private val _projectScope = MutableStateFlow<Scope>(Scope.All)
    val projectScope: StateFlow<Scope> = _projectScope.asStateFlow()

    val projects = MutableStateFlow<List<Project>>(emptyList())
    val stats = MutableStateFlow(Stats(0, 0, 0, 0))
    val entries = MutableStateFlow<List<Entry>>(emptyList())
    val todos = MutableStateFlow<List<Todo>>(emptyList())
    val statuses = MutableStateFlow<List<TodoStatus>>(emptyList())
    val priorities = MutableStateFlow<List<PriorityLevel>>(emptyList())

    /** 账号安全策略 */
    val policyState = MutableStateFlow(PolicyState())

    /** 每次本地数据变化 +1，详情页据此重新读取 */
    val version = MutableStateFlow(0L)

    val toast = MutableStateFlow<Toast?>(null)

    // ---------- 应用锁 ----------

    /** 是否开启应用锁（仅本机设置，不同步） */
    val lockEnabled = MutableStateFlow(false)

    /** 当前是否处于锁定状态 */
    val locked = MutableStateFlow(false)
    private var backgroundAt: Long? = null

    init {
        // 启动时同步读取，避免锁定前闪现数据
        val enabled = repo.getSetting(LOCK_KEY) == "1"
        lockEnabled.value = enabled
        locked.value = enabled && DeviceAuth.isAvailableSafe()

        cloud.onSessionChange = { loggedIn ->
            if (loggedIn) {
                sync.start()
            } else {
                sync.stop()
                wipeIfRequired()
            }
        }
        cloud.onSessionExpired = { show("登录已过期，请重新登录", error = true) }
        scope.launch {
            cloud.load()
            if (cloud.state.value.loggedIn) sync.start()
        }
        refresh()
    }

    fun setScope(s: Scope) {
        _projectScope.value = s
        refresh()
    }

    fun refresh() {
        scope.launch {
            val s = _projectScope.value
            val snapshot = db.io {
                // 当前项目已被删除（例如在其他设备上）时回到「全部项目」
                val ps = repo.listProjects()
                val sc = if (s is Scope.Project && ps.none { it.id == s.id }) Scope.All else s
                Snapshot(sc, ps, repo.stats(), repo.listEntries(sc), repo.listTodos(sc), repo.listStatuses(), repo.listPriorities(), policy.state())
            }
            _projectScope.value = snapshot.scope
            projects.value = snapshot.projects
            stats.value = snapshot.stats
            entries.value = snapshot.entries
            todos.value = snapshot.todos
            statuses.value = snapshot.statuses
            priorities.value = snapshot.priorities
            policyState.value = snapshot.policy
            version.value++
        }
    }

    private class Snapshot(
        val scope: Scope,
        val projects: List<Project>,
        val stats: Stats,
        val entries: List<Entry>,
        val todos: List<Todo>,
        val statuses: List<TodoStatus>,
        val priorities: List<PriorityLevel>,
        val policy: PolicyState,
    )

    /** 在数据库线程执行修改；成功后刷新并安排同步，失败时提示错误 */
    fun <T> mutate(success: String? = null, onDone: (T) -> Unit = {}, block: Repository.() -> T) {
        scope.launch {
            try {
                val r = db.io { repo.block() }
                refresh()
                sync.refreshPending()
                sync.schedule()
                success?.let { show(it) }
                onDone(r)
            } catch (e: Exception) {
                show(e.message ?: "操作失败", error = true)
            }
        }
    }

    /** 读取（不触发同步） */
    fun <T> read(onDone: (T) -> Unit, block: Repository.() -> T) {
        scope.launch {
            try {
                onDone(db.io { repo.block() })
            } catch (e: Exception) {
                show(e.message ?: "读取失败", error = true)
            }
        }
    }

    // ---------- 账号安全策略 ----------

    /** 退出登录（含登录失效）后，策略要求时清除本机数据 */
    private fun wipeIfRequired() {
        scope.launch {
            val wiped = db.io {
                if (policy.state().wipeOnLogout) {
                    policy.wipeLocalData()
                    true
                } else {
                    false
                }
            }
            if (wiped) {
                refresh()
                show("已按账号安全策略清除本机数据")
            }
        }
    }

    /**
     * 退出前的准备：策略要求清除本机数据时先同步一次，返回仍未上传的修改数（不清除时返回 0）。
     */
    suspend fun prepareLogout(): Int {
        if (!policyState.value.wipeOnLogout) return 0
        try {
            sync.syncNow()
        } catch (_: Exception) {
            // 同步失败时按未上传处理
        }
        return db.io { sync.pendingCount() }
    }

    /** 修改策略（需安全码，含 scrypt 计算），成功后同步到该账号的其他设备 */
    suspend fun updatePolicy(code: String?, newCode: String?, allowExport: Boolean, wipeOnLogout: Boolean) {
        val state = db.io { policy.update(code, newCode, allowExport, wipeOnLogout) }
        policyState.value = state
        sync.refreshPending()
        sync.schedule(500)
    }

    fun show(text: String, error: Boolean = false) {
        toast.value = Toast(text, error)
    }

    /** 进入后台时记录时间 */
    fun onBackground() {
        backgroundAt = Dates.nowMs()
    }

    /** 回到前台：离开超过 30 秒需重新验证；同时立即同步一次 */
    fun onResume() {
        val since = backgroundAt
        backgroundAt = null
        if (lockEnabled.value && since != null && Dates.nowMs() - since > RELOCK_MS && DeviceAuth.isAvailableSafe()) locked.value = true
        sync.onResume()
    }

    fun unlock() {
        scope.launch {
            if (DeviceAuth.authenticate("验证身份以查看账号密码")) locked.value = false
        }
    }

    /** 开启前先验证一次，确认设备的验证方式可用 */
    fun setLockEnabled(on: Boolean) {
        scope.launch {
            if (on) {
                if (!DeviceAuth.isAvailable()) {
                    show("请先在系统设置中设置锁屏密码或指纹 / 面容", error = true)
                    return@launch
                }
                if (!DeviceAuth.authenticate("验证身份以开启应用锁")) return@launch
            }
            db.io { repo.setSetting(LOCK_KEY, if (on) "1" else null) }
            lockEnabled.value = on
            show(if (on) "已开启应用锁" else "已关闭应用锁")
        }
    }

    companion object {
        private const val LOCK_KEY = "lock.enabled"
        private const val RELOCK_MS = 30_000L
        private var instance: AppStore? = null

        /** 进程内单例：Activity 重建时不重复打开数据库 */
        fun get(): AppStore = instance ?: AppStore().also { instance = it }
    }
}

/** 设备移除了锁屏方式时不再锁定，否则用户会被锁在应用外 */
private fun DeviceAuth.isAvailableSafe(): Boolean = try { isAvailable() } catch (e: Exception) { false }
