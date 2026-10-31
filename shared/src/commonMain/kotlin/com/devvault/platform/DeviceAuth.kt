package com.devvault.platform

/** 系统身份验证（指纹 / 面容 / 锁屏密码），用于应用锁 */
expect object DeviceAuth {
    /** 设备是否设置了可用的验证方式 */
    fun isAvailable(): Boolean

    /** 弹出系统验证，成功返回 true（用户取消或失败返回 false） */
    suspend fun authenticate(reason: String): Boolean
}
