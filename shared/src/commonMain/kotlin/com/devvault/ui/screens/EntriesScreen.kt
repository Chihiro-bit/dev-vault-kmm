package com.devvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devvault.AppStore
import com.devvault.model.Category
import com.devvault.model.Entry
import com.devvault.totp.Totp
import com.devvault.ui.CAT_MAP
import com.devvault.ui.CATEGORIES
import com.devvault.ui.Navigator
import com.devvault.ui.Overlay
import com.devvault.ui.components.EmptyState
import com.devvault.ui.components.EnvTag
import com.devvault.ui.components.ExpiryTag
import com.devvault.ui.components.FilterPill
import com.devvault.ui.components.IconBtn
import com.devvault.ui.components.PageHeader
import com.devvault.ui.components.ProjTag
import com.devvault.ui.components.SearchField
import com.devvault.ui.components.TotpRing
import com.devvault.ui.components.dvCard
import com.devvault.ui.components.rememberCopier
import com.devvault.ui.isHttpUrl
import com.devvault.ui.matchesKeyword
import com.devvault.ui.scopeLabel
import com.devvault.ui.splitTags
import com.devvault.ui.theme.Dv
import com.devvault.ui.theme.DvIcon
import com.devvault.ui.theme.DvIconView
import com.devvault.ui.theme.weak
import com.devvault.util.Dates
import kotlinx.coroutines.delay

private sealed interface EntryFilter {
    data object All : EntryFilter
    data class Cat(val c: Category) : EntryFilter
    data object Fav : EntryFilter
    data object Expiring : EntryFilter
}

private const val EXPIRY_WARN_DAYS = 7

private fun isExpiring(e: Entry) = e.expiresAt.isNotEmpty() && Dates.daysUntil(e.expiresAt) <= EXPIRY_WARN_DAYS

@Composable
fun EntriesScreen(nav: Navigator, onPickProject: () -> Unit) {
    val store = remember { AppStore.get() }
    val all by store.entries.collectAsState()
    var keyword by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf<EntryFilter>(EntryFilter.All) }
    val c = Dv.colors

    val searched = remember(all, keyword) {
        all.filter {
            matchesKeyword(keyword, it.title, it.username, it.url, it.verifyCode, it.notes, it.tags, it.env, it.projectName,
                it.extra.filter { f -> !f.secret }.joinToString(" ") { f -> "${f.key} ${f.value}" })
        }
    }
    val shown = remember(searched, filter) {
        when (val f = filter) {
            EntryFilter.All -> searched
            is EntryFilter.Cat -> searched.filter { it.category == f.c }
            EntryFilter.Fav -> searched.filter { it.favorite }
            EntryFilter.Expiring -> searched.filter(::isExpiring).sortedBy { it.expiresAt }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader("账号密码", scopeLabel(), onSubtitleClick = onPickProject)
            Column(Modifier.background(c.panel).padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                SearchField(keyword, { keyword = it }, "搜索名称、账号、地址、备注、标签")
            }
            com.devvault.ui.components.Hairline()
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterPill("全部", searched.size, filter == EntryFilter.All) { filter = EntryFilter.All } }
                items(CATEGORIES) { m ->
                    FilterPill(m.label, searched.count { it.category == m.key }, filter == EntryFilter.Cat(m.key), icon = m.icon) {
                        filter = EntryFilter.Cat(m.key)
                    }
                }
                item {
                    FilterPill("收藏", searched.count { it.favorite }, filter == EntryFilter.Fav, icon = DvIcon.Star, activeColor = c.star) {
                        filter = EntryFilter.Fav
                    }
                }
                item {
                    FilterPill("即将过期", searched.count(::isExpiring), filter == EntryFilter.Expiring, icon = DvIcon.Calendar, activeColor = c.warn) {
                        filter = EntryFilter.Expiring
                    }
                }
            }
            if (shown.isEmpty()) {
                EmptyState(
                    DvIcon.Key,
                    if (all.isEmpty()) "还没有记录" else "没有匹配的记录",
                    if (all.isEmpty()) "点击右下角「新建记录」添加测试账号、Git、VPN 等凭据" else "换个关键词或筛选条件试试",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { e ->
                        EntryCard(e, onEdit = { nav.push(Overlay.EditEntry(e)) })
                    }
                }
            }
        }
        // 新建记录（.btn.primary）
        Row(
            Modifier.align(Alignment.BottomEnd).padding(16.dp)
                .shadow(6.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp))
                .background(c.primary).clickable { nav.push(Overlay.EditEntry(null)) }
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DvIconView(DvIcon.Plus, Color.White, 18.dp)
            Spacer(Modifier.width(6.dp))
            Text("新建记录", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** 每秒刷新的时间戳，用于动态码 */
@Composable
fun rememberTicker(): Long {
    val now by produceState(Dates.nowMs()) {
        while (true) {
            delay(1000 - value % 1000)
            value = Dates.nowMs()
        }
    }
    return now
}

/** .cat-icon：类型图标（带底色的圆角方块） */
@Composable
fun CatIcon(category: Category, size: Int = 36) {
    val m = CAT_MAP.getValue(category)
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(m.color.weak(0.12f)),
        contentAlignment = Alignment.Center,
    ) { DvIconView(m.icon, m.color, (size / 2).dp) }
}

@Composable
fun EntryCard(e: Entry, onEdit: () -> Unit) {
    val store = remember { AppStore.get() }
    val copy = rememberCopier()
    val uri = LocalUriHandler.current
    val m = CAT_MAP.getValue(e.category)
    val c = Dv.colors

    Column(Modifier.fillMaxWidth().dvCard().clickable(onClick = onEdit).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            CatIcon(e.category)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        e.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    EnvTag(e.env)
                }
                FlowRow(
                    Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(m.label, fontSize = 12.sp, color = c.muted)
                    ProjTag(e.projectName, e.projectColor)
                    ExpiryTag(e.expiresAt)
                }
            }
            IconBtn(DvIcon.Star, { store.mutate<Unit> { toggleFavorite(e.id) } }, tint = if (e.favorite) c.star else c.borderStrong, filled = e.favorite)
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (e.url.isNotEmpty()) {
                KvRow(m.urlLabel, e.url, onCopy = { copy(e.url, m.urlLabel, false) }) {
                    if (isHttpUrl(e.url)) IconBtn(DvIcon.External, { uri.openUri(e.url) }, size = 15.dp, box = 30.dp)
                }
            }
            if (e.username.isNotEmpty()) KvRow(m.userLabel, e.username, onCopy = { copy(e.username, m.userLabel, false) })
            if (e.password.isNotEmpty()) SecretRow(m.pwdLabel, e.password) { copy(e.password, m.pwdLabel, true) }
            if (e.verifyCode.isNotEmpty()) {
                KvRow(m.codeLabel, e.verifyCode, code = true, onCopy = { copy(e.verifyCode, m.codeLabel, false) })
            }
            if (e.totp.isNotEmpty()) TotpRow(e.totp)
            e.extra.forEach { f ->
                if (f.secret) SecretRow(f.key.ifEmpty { "字段" }, f.value) { copy(f.value, f.key, true) }
                else KvRow(f.key.ifEmpty { "字段" }, f.value, onCopy = { copy(f.value, f.key, false) })
            }
        }
        if (e.notes.isNotBlank() || e.tags.isNotBlank()) {
            Column(Modifier.padding(start = 4.dp, end = 10.dp, top = 8.dp)) {
                if (e.notes.isNotBlank()) {
                    Text(e.notes, fontSize = 13.sp, color = c.text2, lineHeight = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                val tags = splitTags(e.tags)
                if (tags.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.forEach { Text("#$it", fontSize = 12.sp, color = c.primary) }
                    }
                }
            }
        }
    }
}

/** .kv-row：浅灰底的一行「标签 — 值」，点击值复制 */
@Composable
fun KvRow(
    label: String,
    value: String,
    code: Boolean = false,
    masked: Boolean = false,
    valueColor: Color? = null,
    onCopy: () -> Unit,
    valueContent: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val c = Dv.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 38.dp).clip(RoundedCornerShape(6.dp)).background(c.bg)
            .clickable(onClick = onCopy).padding(start = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(84.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (valueContent != null) {
                valueContent()
            } else if (code) {
                Text(
                    value, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.5.sp,
                    color = c.codeText, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(c.codeBg).padding(horizontal = 8.dp, vertical = 1.dp),
                )
            } else {
                Text(
                    value, fontFamily = FontFamily.Monospace, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = valueColor ?: if (masked) c.text2 else c.text, letterSpacing = if (masked) 2.sp else 0.sp,
                )
            }
        }
        actions()
        IconBtn(DvIcon.Copy, onCopy, size = 15.dp, box = 30.dp)
    }
}

@Composable
fun SecretRow(label: String, value: String, onCopy: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    KvRow(label, if (shown) value else "••••••••", masked = !shown, onCopy = onCopy) {
        IconBtn(if (shown) DvIcon.EyeOff else DvIcon.Eye, { shown = !shown }, size = 15.dp, box = 30.dp)
    }
}

/** 动态码：实时显示 6 位码与倒计时圆环 */
@Composable
fun TotpRow(secret: String) {
    val c = Dv.colors
    val copy = rememberCopier()
    val cfg = remember(secret) { Totp.parse(secret) }
    if (cfg == null) {
        KvRow("动态码", "密钥无效", valueColor = c.danger, onCopy = {})
        return
    }
    val now = rememberTicker()
    val code = remember(cfg, now / 1000 / cfg.period) { Totp.code(cfg, now) }
    val remaining = Totp.remaining(cfg, now)
    KvRow("动态码", "", onCopy = { copy(code, "动态码", false) }, valueContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Totp.format(code), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                letterSpacing = 2.sp, color = c.primary,
            )
            Spacer(Modifier.width(10.dp))
            TotpRing(remaining, cfg.period)
            Spacer(Modifier.width(5.dp))
            Text("${remaining}s", fontSize = 11.sp, color = c.muted, modifier = Modifier.width(26.dp))
        }
    })
}
