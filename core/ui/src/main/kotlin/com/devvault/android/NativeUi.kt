package com.devvault.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.devvault.core.ui.R
import com.devvault.core.ui.databinding.ViewEmptyBinding
import com.devvault.core.ui.databinding.ViewPillBinding
import kotlin.math.roundToInt

/*
 * 界面结构全部写在 res/layout 中；这里只放绑定数据时用到的小工具。
 * 静态背景使用 res/drawable/bg_*.xml，只有颜色来自数据（类型、项目、优先级、状态）时才用 shape() 动态生成。
 */

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
fun Context.color(id: Int) = getColor(id)
val View.inflater: LayoutInflater get() = LayoutInflater.from(context)

fun parseColor(value: String?, fallback: Int = Color.GRAY) = try { Color.parseColor(value) } catch (_: Exception) { fallback }

/** 浅色底：与桌面端 color-mix 的 12% 透明度一致 */
fun weak(color: Int, alpha: Float = .12f) = (color and 0xffffff) or ((255 * alpha).toInt() shl 24)

/** 颜色由数据决定的圆角背景 */
fun Context.shape(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
    setColor(fill); cornerRadius = dp(radius).toFloat(); stroke?.let { setStroke(dp(1), it) }
}

fun Context.icon(icon: DvIcon, tint: Int): Drawable =
    AppCompatResources.getDrawable(this, icon.res)!!.mutate().apply { setTint(tint) }

fun ImageView.setIcon(icon: DvIcon, tint: Int) {
    setImageResource(icon.res); ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
}

fun ImageView.tint(tint: Int) = ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))

/** 设置文字，空白时隐藏 */
fun TextView.showText(value: CharSequence?) { text = value; isVisible = !value.isNullOrBlank() }

fun EditText.value() = text.toString()

/**
 * 在 parent 末尾加入一个 view_pill.xml 胶囊。
 * solid：选中时实心填充（筛选栏）；否则为浅色底 + 彩色描边（表单选项）。
 */
fun ViewGroup.addPill(text: String, selected: Boolean, tone: Int = context.color(R.color.primary), icon: DvIcon? = null, solid: Boolean = true, onClick: () -> Unit): View {
    val pill = ViewPillBinding.inflate(inflater, this, true)
    val fg = when { selected && solid -> Color.WHITE; selected -> tone; else -> context.color(R.color.text2) }
    if (selected) pill.root.background = context.shape(if (solid) tone else weak(tone), 16, tone)
    pill.pillText.text = text; pill.pillText.setTextColor(fg)
    if (icon != null) { pill.pillIcon.isVisible = true; pill.pillIcon.setIcon(icon, fg) }
    pill.root.isSelected = selected
    pill.root.setOnClickListener { onClick() }
    return pill.root
}

/** 空状态（view_empty.xml） */
fun Context.emptyView(icon: DvIcon, title: String, subtitle: String): View {
    val b = ViewEmptyBinding.inflate(LayoutInflater.from(this))
    b.emptyIcon.setImageResource(icon.res); b.emptyTitle.text = title; b.emptySubtitle.text = subtitle
    return b.root
}

/** 把 XML 中的 DvButton.Select 按钮绑定为下拉选择，返回读取当前值的函数 */
fun <T> Button.bindChoice(values: List<T>, selected: T, display: (T) -> String, onChange: (T) -> Unit = {}): () -> T {
    var value = selected.takeIf { it in values } ?: values.first()
    text = display(value)
    setOnClickListener {
        PopupMenu(context, this).apply {
            values.forEachIndexed { i, item -> menu.add(0, i, i, display(item)) }
            setOnMenuItemClickListener { value = values[it.itemId]; text = display(value); onChange(value); true }
        }.show()
    }
    return { value }
}

/** 项目选择（未分组 + 全部项目） */
fun Button.bindProject(activity: VaultActivity, selected: Long?, onChange: (Long?) -> Unit = {}): () -> Long? {
    val projects = activity.store.projects.value
    return bindChoice(listOf<Long?>(null) + projects.map { it.id }, selected, { id -> projects.find { it.id == id }?.name ?: activity.getString(R.string.unassigned) }, onChange)
}

/** 自动换行排列胶囊，可直接在 XML 中使用 <com.devvault.android.ChipFlow> */
class ChipFlow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {
    private val gap = context.dp(8)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var line = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i); if (!child.isVisible) continue
            measureChild(child, widthMeasureSpec, heightMeasureSpec)
            if (x > 0 && x + child.measuredWidth > width) { x = 0; y += line + gap; line = 0 }
            x += child.measuredWidth + gap; line = maxOf(line, child.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(y + line + paddingTop + paddingBottom, heightMeasureSpec))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var line = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i); if (!child.isVisible) continue
            if (x > 0 && x + child.measuredWidth > width) { x = 0; y += line + gap; line = 0 }
            child.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + child.measuredWidth, paddingTop + y + child.measuredHeight)
            x += child.measuredWidth + gap; line = maxOf(line, child.measuredHeight)
        }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
}
