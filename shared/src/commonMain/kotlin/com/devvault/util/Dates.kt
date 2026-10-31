package com.devvault.util

import com.devvault.model.Repeat
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

object Dates {
    private val tz get() = TimeZone.currentSystemDefault()

    fun nowMs(): Long = Clock.System.now().toEpochMilliseconds()

    /** YYYY-MM-DD */
    fun today(): String = Clock.System.todayIn(tz).toString()

    /** ISO 8601（UTC），用于记录同步时间 */
    fun nowIso(): String = Clock.System.now().toString()

    /** 本地时间 "MM-DD HH:mm"，用于显示同步时间 */
    fun formatIsoShort(iso: String): String = try {
        val t = kotlinx.datetime.Instant.parse(iso).toLocalDateTime(tz)
        "${pad(t.monthNumber)}-${pad(t.dayOfMonth)} ${pad(t.hour)}:${pad(t.minute)}"
    } catch (e: Exception) {
        iso
    }

    fun pad(n: Int) = n.toString().padStart(2, '0')

    fun parse(date: String): LocalDate? = try {
        LocalDate.parse(date)
    } catch (e: Exception) {
        null
    }

    fun addDays(date: String, n: Int): String = parse(date)?.plus(n, DateTimeUnit.DAY)?.toString() ?: date

    fun daysBetween(from: String, to: String): Int {
        val a = parse(from) ?: return 0
        val b = parse(to) ?: return 0
        return a.daysUntil(b)
    }

    /** 距离某天还有几天（今天为 0，过去为负数） */
    fun daysUntil(date: String): Int = daysBetween(today(), date)

    private fun advance(d: LocalDate, repeat: Repeat): LocalDate = when (repeat) {
        Repeat.DAILY -> d.plus(1, DateTimeUnit.DAY)
        Repeat.WEEKLY -> d.plus(7, DateTimeUnit.DAY)
        Repeat.WEEKDAYS -> {
            var x = d.plus(1, DateTimeUnit.DAY)
            while (x.dayOfWeek == DayOfWeek.SATURDAY || x.dayOfWeek == DayOfWeek.SUNDAY) x = x.plus(1, DateTimeUnit.DAY)
            x
        }
        // kotlinx-datetime 按月增加时自动夹到月末，与桌面端逻辑一致
        Repeat.MONTHLY -> d.plus(1, DateTimeUnit.MONTH)
        Repeat.NONE -> d
    }

    /** 下一次的截止日期：从原截止日期（无则今天）起按规则推进，直到不早于今天 */
    fun nextDueDate(due: String, repeat: Repeat, today: String = today()): String {
        var d = parse(due.ifEmpty { today }) ?: parse(today)!!
        do d = advance(d, repeat) while (d.toString() < today)
        return d.toString()
    }

    /** 截止日期的展示文案（逾期 / 今天到期） */
    data class Due(val text: String, val tone: DueTone)
    enum class DueTone { NORMAL, TODAY, OVERDUE }

    fun dueInfo(dueDate: String, done: Boolean): Due? {
        if (dueDate.isEmpty()) return null
        val d = today()
        if (done) return Due(dueDate, DueTone.NORMAL)
        if (dueDate < d) return Due("逾期 ${dueDate.drop(5)}", DueTone.OVERDUE)
        if (dueDate == d) return Due("今天到期", DueTone.TODAY)
        return Due(if (dueDate.take(4) == d.take(4)) dueDate.drop(5) else dueDate, DueTone.NORMAL)
    }

    enum class ExpiryTone { OK, SOON, EXPIRED }
    data class Expiry(val text: String, val tone: ExpiryTone)

    /** 账号有效期：已过期 / N 天内过期 / 长期 */
    fun expiryInfo(expires: String, warnDays: Int = 7): Expiry? {
        if (expires.isEmpty()) return null
        val n = daysUntil(expires)
        return when {
            n < 0 -> Expiry("已过期 ${-n} 天", ExpiryTone.EXPIRED)
            n == 0 -> Expiry("今天过期", ExpiryTone.EXPIRED)
            n <= warnDays -> Expiry("$n 天后过期", ExpiryTone.SOON)
            else -> Expiry(expires, ExpiryTone.OK)
        }
    }
}
