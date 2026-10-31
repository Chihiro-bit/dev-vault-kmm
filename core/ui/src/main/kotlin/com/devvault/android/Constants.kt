package com.devvault.android

import com.devvault.core.ui.R

import android.graphics.Color
import com.devvault.model.Category


/** 与桌面端 constants.ts 的 CATEGORIES 一致：字段名随类型变化 */
data class CategoryMeta(
    val key: Category,
    val label: String,
    val icon: DvIcon,
    val color: Int,
    val urlLabel: String,
    val userLabel: String,
    val pwdLabel: String,
    val codeLabel: String,
    val showCode: Boolean,
    val urlPlaceholder: String,
    val presets: List<Pair<String, Boolean>>,
)

val CATEGORIES = listOf(
    CategoryMeta(Category.ACCOUNT, "测试账号", DvIcon.User, 0xFF3B6CFF.toInt(), "登录地址", "账号", "密码", "固定验证码", true,
        "https://test.example.com/login", listOf("手机号" to false, "角色" to false, "租户 / 组织" to false, "支付密码" to true)),
    CategoryMeta(Category.GIT, "Git", DvIcon.Git, 0xFFF05133.toInt(), "平台 / 仓库地址", "用户名", "密码 / Token", "验证码", false,
        "https://gitlab.company.com", listOf("SSH 地址" to false, "Access Token" to true, "邮箱" to false)),
    CategoryMeta(Category.VPN, "VPN", DvIcon.Shield, 0xFF16A37B.toInt(), "服务器地址", "用户名", "密码", "动态码 / 验证码", true,
        "vpn.company.com:443", listOf("协议" to false, "共享密钥" to true, "客户端下载" to false)),
    CategoryMeta(Category.SERVER, "服务器", DvIcon.Server, 0xFF9B5CF6.toInt(), "主机 / IP", "用户名", "密码", "验证码", false,
        "192.168.1.100", listOf("端口" to false, "密钥路径" to false, "跳板机" to false)),
    CategoryMeta(Category.DATABASE, "数据库", DvIcon.Database, 0xFF0EA5C6.toInt(), "连接地址", "用户名", "密码", "验证码", false,
        "mysql://10.0.0.8:3306", listOf("端口" to false, "库名" to false, "类型" to false)),
    CategoryMeta(Category.OTHER, "其他", DvIcon.Key, 0xFF64748B.toInt(), "地址", "账号", "密码", "验证码", true,
        "https://", listOf("说明" to false, "密钥" to true)),
)

val CAT_MAP = CATEGORIES.associateBy { it.key }

val ENVS = listOf("开发", "测试", "预发", "生产")

/** 环境标签颜色：开发蓝 / 测试绿 / 预发橙 / 生产红 */
fun envColor(env: String): Int? = when (env) {
    "开发" -> 0xFF3B6CFF.toInt()
    "测试" -> 0xFF16A37B.toInt()
    "预发" -> 0xFFD97706.toInt()
    "生产" -> 0xFFE5484D.toInt()
    else -> null
}

val PROJECT_COLORS = listOf("#3b6cff", "#16a37b", "#f59e0b", "#e5484d", "#9b5cf6", "#0ea5c6", "#e1488f", "#64748b")

fun splitTags(tags: String) = tags.split(',').filter { it.isNotBlank() }

fun isHttpUrl(s: String) = Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)

fun generatePassword(len: Int = 16): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*_-"
    val bytes = com.devvault.platform.Platform.randomBytes(len * 2)
    return (0 until len).joinToString("") {
        val n = ((bytes[it * 2].toInt() and 0xff) shl 8) or (bytes[it * 2 + 1].toInt() and 0xff)
        chars[n % chars.length].toString()
    }
}

/** 空格分隔多个关键词，全部命中才算匹配 */
fun matchesKeyword(keyword: String, vararg fields: String?): Boolean {
    val words = keyword.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val hay = fields.filterNotNull().joinToString("\n").lowercase()
    return words.all { it in hay }
}
