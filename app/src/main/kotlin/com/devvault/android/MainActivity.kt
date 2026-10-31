package com.devvault.android

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.devvault.android.databinding.ActivityMainBinding
import com.devvault.android.databinding.DialogProjectPickerBinding
import com.devvault.android.databinding.ItemNavBinding
import com.devvault.android.databinding.ItemProjectChoiceBinding
import com.devvault.model.Entry
import com.devvault.model.Scope
import com.devvault.util.Dates
import kotlinx.coroutines.launch
import com.devvault.core.ui.R as CR

/** 宽度达到此值（dp）时使用 layout-w600dp：左侧导航栏 */
private const val WIDE_DP = 600

/** 应用入口：底部三个页签（账号密码 / 待办事项 / 设置），内容由各功能模块提供 */
class MainActivity : VaultActivity() {
    private lateinit var binding: ActivityMainBinding
    private var tab = 0
    private var entryFilter = 0
    private val records = ScreenAdapter()
    private val entries by lazy { EntryScreens(this) }
    private val todos by lazy { TodoScreens(this) }
    private val settings by lazy { SettingsScreens(this) }
    private fun tabs() = listOf(
        Triple(binding.navEntries, R.string.tab_entries, DvIcon.Key),
        Triple(binding.navTodos, R.string.tab_todos, DvIcon.Check),
        Triple(binding.navSettings, R.string.tab_settings, DvIcon.Gear),
    )
    /** 当前加载的是否为 layout-w600dp 版本（左侧导航栏） */
    private var wideLayout = false
    /** 每次加载布局都新建（LayoutManager 不能同时挂在新旧两个 RecyclerView 上） */
    private lateinit var grid: GridLayoutManager
    /** 账号页的卡片列数：1 或 2，由列表实际宽度决定 */
    private var columns = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            val light = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
            isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
        }
        tab = savedInstanceState?.getInt("tab") ?: 0
        entryFilter = savedInstanceState?.getInt("entryFilter") ?: 0
        inflateLayout()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { store.version.collect { render() } }
                launch { store.cloud.state.collect { if (tab == 2) render() } }
                launch { store.lockEnabled.collect { if (tab == 2) render() } }
                launch { store.policyState.collect { if (tab == 2) render() } }
                launch {
                    store.locked.collect { locked ->
                        if (locked) { closeSensitivePages(); entries.clearSecrets(); settings.clearSecrets() }
                        binding.lock.isVisible = locked
                        binding.main.importantForAccessibility = if (locked) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    }
                }
                launch { store.toast.collect { toast -> toast?.let { Toast.makeText(this@MainActivity, it.text, Toast.LENGTH_LONG).show(); store.toast.value = null } } }
            }
        }
        render()
    }

    override fun onResume() { super.onResume(); store.onResume() }
    override fun onStop() { store.onBackground(); super.onStop() }
    override fun onSaveInstanceState(out: Bundle) { out.putInt("tab", tab); out.putInt("entryFilter", entryFilter); super.onSaveInstanceState(out) }

    /**
     * 清单中声明了 configChanges（旋转、分屏、折叠屏展开时不重建 Activity，以免丢失正在编辑的弹窗），
     * 因此系统不会自动换用 layout-w600dp。宽度跨过 600dp 时在这里重新加载主界面布局。
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if ((newConfig.screenWidthDp >= WIDE_DP) != wideLayout) {
            val query = binding.search.text.toString()
            inflateLayout()
            binding.search.setText(query)
            binding.lock.isVisible = store.locked.value
            render()
        }
    }

    private fun inflateLayout() {
        if (::binding.isInitialized) { binding.list.adapter = null; binding.list.layoutManager = null }
        wideLayout = resources.configuration.screenWidthDp >= WIDE_DP
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val e = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(e.left, e.top, e.right, e.bottom); insets
        }
        grid = GridLayoutManager(this, columns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int) = if (records.isFullSpan(position)) spanCount else 1
            }
        }
        binding.list.layoutManager = grid
        binding.list.adapter = records
        // 列表宽度变化（旋转、分屏拖动、折叠屏）时重新计算居中内容列
        binding.list.addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) binding.list.post { updateContentWidth() } }
        binding.search.doAfterTextChanged { render() }
        binding.project.setOnClickListener { chooseProject() }
        binding.add.setOnClickListener { entries.edit(Entry(projectId = currentProject())) }
        binding.unlock.setOnClickListener { store.unlock() }
        tabs().forEachIndexed { i, (nav, name, icon) ->
            nav.navLabel.setText(name); nav.navIcon.setImageResource(icon.res); nav.root.contentDescription = getString(name)
            nav.root.setOnClickListener { if (tab != i) { tab = i; binding.search.setText(""); render(); binding.list.scrollToPosition(0) } }
        }
    }

    /**
     * 大屏适配：内容列不超过 content_max_width（账号双列时为 content_max_width_wide），多出的宽度作为两侧留白居中；
     * 列表宽度达到 two_column_min_width 时账号卡片改为两列。
     */
    private fun updateContentWidth() {
        val width = binding.list.width
        if (width == 0) return
        val wantColumns = if (tab == 0 && width >= resources.getDimensionPixelSize(CR.dimen.two_column_min_width)) 2 else 1
        val maxWidth = resources.getDimensionPixelSize(if (wantColumns == 2) CR.dimen.content_max_width_wide else CR.dimen.content_max_width)
        val side = maxOf(resources.getDimensionPixelSize(CR.dimen.page_margin), (width - maxWidth) / 2)
        val gap = if (wantColumns == 2) dp(6) else 0
        val bottom = dp(if (tab == 0) 96 else 32)
        if (binding.list.paddingLeft != side - gap || binding.list.paddingBottom != bottom) binding.list.setPadding(side - gap, 0, side - gap, bottom)
        if (binding.filterScroll.paddingLeft != side) binding.filterScroll.setPadding(side, binding.filterScroll.paddingTop, side, binding.filterScroll.paddingBottom)
        records.gap = gap
        if (wantColumns != columns) { columns = wantColumns; grid.spanCount = wantColumns; records.refresh() }
    }

    override fun render() {
        if (!::binding.isInitialized) return
        val tabs = tabs()
        binding.title.setText(tabs[tab].second)
        binding.project.text = when (val s = store.projectScope.value) {
            Scope.All -> getString(CR.string.all_projects)
            Scope.None -> getString(CR.string.unassigned)
            is Scope.Project -> store.projects.value.find { it.id == s.id }?.name ?: getString(CR.string.all_projects)
        }
        binding.project.isVisible = tab != 2
        binding.search.isVisible = tab != 2
        binding.filterScroll.isVisible = tab == 0
        binding.add.isVisible = tab == 0
        binding.search.setHint(if (tab == 0) R.string.search_entries_hint else R.string.search_todos_hint)
        val query = binding.search.text.toString()
        when (tab) {
            // 只有账号卡片可以并排两列，筛选结果为空时的提示占满整行
            0 -> entryRows(query).let { (views, cards) -> records.submit(views) { it < views.size && !cards } }
            1 -> records.submit(todos.rows(query)) { true }
            else -> records.submit(listOf(settings.content())) { true }
        }
        updateContentWidth()
        renderNavigation()
    }

    private fun renderNavigation() {
        tabs().forEachIndexed { i, (nav, _, _) -> bindNav(nav, tab == i) }
        val count = store.stats.value.todos
        binding.navTodos.navBadge.isVisible = count > 0
        binding.navTodos.navBadge.text = if (count > 99) "99+" else count.toString()
    }

    private fun bindNav(nav: ItemNavBinding, selected: Boolean) {
        val tint = color(if (selected) CR.color.primary else CR.color.muted)
        nav.navIcon.tint(tint); nav.navLabel.setTextColor(tint); nav.root.isSelected = selected
    }

    /** 返回列表行，以及这些行是否为账号卡片（false 表示空状态提示） */
    private fun entryRows(query: String): Pair<List<View>, Boolean> {
        val all = store.entries.value
        val searched = all.filter {
            matchesKeyword(query, it.title, it.username, it.url, it.verifyCode, it.notes, it.tags, it.env, it.projectName,
                it.extra.filter { f -> !f.secret }.joinToString { f -> "${f.key} ${f.value}" })
        }
        fun expiring(e: Entry) = e.expiresAt.isNotBlank() && Dates.daysUntil(e.expiresAt) <= 7
        binding.filters.removeAllViews()
        fun chip(index: Int, name: String, count: Int, icon: DvIcon? = null, tone: Int = color(CR.color.primary)) {
            binding.filters.addPill("$name  $count", entryFilter == index, tone, icon) { entryFilter = index; render() }
                .apply { (layoutParams as ViewGroup.MarginLayoutParams).marginEnd = dp(8) }
        }
        chip(0, "全部", searched.size)
        CATEGORIES.forEachIndexed { i, m -> chip(i + 1, m.label, searched.count { it.category == m.key }, m.icon) }
        chip(7, "收藏", searched.count { it.favorite }, DvIcon.Star, color(CR.color.star))
        chip(8, "即将过期", searched.count(::expiring), DvIcon.Calendar, color(CR.color.warn))
        val shown = when (entryFilter) {
            in 1..6 -> searched.filter { it.category == CATEGORIES[entryFilter - 1].key }
            7 -> searched.filter { it.favorite }
            8 -> searched.filter(::expiring).sortedBy { it.expiresAt }
            else -> searched
        }
        return if (shown.isEmpty()) listOf(emptyView(
            DvIcon.Key,
            if (all.isEmpty()) "还没有记录" else "没有匹配的记录",
            if (all.isEmpty()) "点击右下角「新建记录」添加测试账号、Git、VPN 等凭据" else "换个关键词或筛选条件试试",
        )) to false else shown.map(entries::card) to true
    }

    private fun chooseProject() {
        val dialog = Dialog(this)
        val b = DialogProjectPickerBinding.inflate(layoutInflater)
        b.newProject.setOnClickListener { dialog.dismiss(); settings.project(null) }
        val stats = store.stats.value
        val choices = listOf(
            Triple<Scope, String, Int>(Scope.All, getString(CR.string.all_projects), stats.entries + stats.todos),
            Triple(Scope.None, getString(CR.string.unassigned), stats.noneEntries + stats.noneTodos),
        ) + store.projects.value.map { Triple(Scope.Project(it.id), it.name, it.entryCount + it.todoCount) }
        choices.forEach { (scope, name, count) ->
            val row = ItemProjectChoiceBinding.inflate(layoutInflater, b.choices, true)
            val selected = scope == store.projectScope.value
            row.choiceName.text = name; row.choiceCount.text = count.toString()
            if (selected) {
                row.root.setBackgroundResource(CR.drawable.bg_choice_selected)
                row.choiceName.setTextColor(color(CR.color.text))
                row.choiceName.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            }
            (scope as? Scope.Project)?.let { s ->
                row.choiceDot.isVisible = true
                row.choiceDot.setTextColor(parseColor(store.projects.value.find { it.id == s.id }?.color))
            }
            row.root.setOnClickListener { store.setScope(scope); dialog.dismiss() }
        }
        dialog.setContentView(b.root); track(dialog); dialog.show()
        dialog.window?.apply { setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); setGravity(Gravity.BOTTOM); setLayout(cappedWidth(1f, CR.dimen.sheet_max_width), -2) }
    }
}

/** 列表行由各页面按 XML 布局生成，这里只负责承载与回收；双列时卡片两侧各留 gap 作为列间距 */
private class ScreenAdapter : RecyclerView.Adapter<ScreenAdapter.Holder>() {
    private var views = emptyList<View>()
    private var fullSpan: (Int) -> Boolean = { true }
    var gap = 0
    class Holder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

    fun isFullSpan(position: Int) = fullSpan(position)

    @SuppressLint("NotifyDataSetChanged") // 每次重绘都是整页新数据
    fun submit(value: List<View>, fullSpan: (Int) -> Boolean) { views = value; this.fullSpan = fullSpan; notifyDataSetChanged() }

    @SuppressLint("NotifyDataSetChanged")
    fun refresh() = notifyDataSetChanged()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(FrameLayout(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) })
    override fun getItemCount() = views.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.frame.removeAllViews()
        holder.frame.setPadding(gap, 0, gap, 0)
        val view = views[position]; (view.parent as? ViewGroup)?.removeView(view)
        holder.frame.addView(view, FrameLayout.LayoutParams(-1, -2).apply { bottomMargin = view.context.dp(12) })
    }
}
