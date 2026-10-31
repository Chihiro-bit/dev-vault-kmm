package com.devvault.sync

/**
 * iOS 的 URLSession 无法关闭 SNI，暂不支持兼容线路；
 * supported 为 false 时 CloudHttp 直接使用常规连接。
 */
internal actual object SniFreeHttp {
    actual val supported: Boolean = false

    actual suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Int): HttpResult =
        throw UnsupportedOperationException("当前平台不支持备用连接")
}
