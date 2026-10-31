package com.devvault.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket

/**
 * 不携带 SNI 的 HTTPS 请求，对应桌面端 cloud-http.ts 的 fetchWithoutSni：
 * 以 IP 地址建立 TLS 连接（IP 不会作为 SNI 发送），系统信任库在握手时校验证书链，
 * 握手后再用原始主机名校验证书的 SAN，Host 头保持为原始主机名。
 */
internal actual object SniFreeHttp {
    actual val supported: Boolean = true

    private const val MAX_RESPONSE = 50 * 1024 * 1024

    actual suspend fun request(url: String, method: String, headers: Map<String, String>, body: String?, timeoutMs: Int): HttpResult =
        withContext(Dispatchers.IO) {
            val uri = URI(url)
            require(uri.scheme.equals("https", ignoreCase = true) && uri.rawUserInfo == null) { "备用连接仅支持不含凭据的 HTTPS 地址" }
            val host = uri.host
            val port = if (uri.port > 0) uri.port else 443
            val address = InetAddress.getByName(host)

            val plain = Socket()
            plain.connect(InetSocketAddress(address, port), timeoutMs)
            plain.soTimeout = timeoutMs
            val factory = SSLContext.getDefault().socketFactory
            val socket = factory.createSocket(plain, address.hostAddress, port, true) as SSLSocket
            socket.use { ssl ->
                ssl.sslParameters = ssl.sslParameters.apply { serverNames = emptyList() }
                ssl.startHandshake()
                val cert = ssl.session.peerCertificates.firstOrNull() as? X509Certificate
                if (cert == null || !hostnameMatches(cert, host)) throw SSLPeerUnverifiedException("证书与主机名 $host 不匹配")

                val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
                val payload = body?.toByteArray(Charsets.UTF_8)
                val head = buildString {
                    append("$method $path HTTP/1.1\r\n")
                    append("Host: ").append(if (port == 443) host else "$host:$port").append("\r\n")
                    append("Connection: close\r\n")
                    append("Accept: application/json\r\n")
                    append("Accept-Encoding: identity\r\n")
                    headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
                    if (payload != null || method != "GET") append("Content-Length: ${payload?.size ?: 0}\r\n")
                    append("\r\n")
                }
                val out = ssl.outputStream
                out.write(head.toByteArray(Charsets.UTF_8))
                if (payload != null) out.write(payload)
                out.flush()
                parse(readAll(ssl.inputStream), method)
            }
        }

    private fun readAll(input: InputStream): ByteArray {
        val buf = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            buf.write(chunk, 0, n)
            if (buf.size() > MAX_RESPONSE) throw IllegalStateException("服务器响应过大")
        }
        return buf.toByteArray()
    }

    private fun parse(raw: ByteArray, method: String): HttpResult {
        val sep = indexOf(raw, "\r\n\r\n".toByteArray(), 0)
        if (sep < 0) throw IllegalStateException("服务器响应不完整")
        val lines = String(raw, 0, sep, Charsets.ISO_8859_1).split("\r\n")
        val status = lines.first().split(' ').getOrNull(1)?.toIntOrNull() ?: throw IllegalStateException("无法解析服务器响应")
        val headers = lines.drop(1).mapNotNull { line ->
            val i = line.indexOf(':')
            if (i <= 0) null else line.substring(0, i).trim().lowercase() to line.substring(i + 1).trim()
        }.toMap()
        var body = raw.copyOfRange(sep + 4, raw.size)
        if (method == "HEAD" || status in listOf(204, 205, 304)) return HttpResult(status, "")
        if (headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true) {
            body = dechunk(body)
        } else {
            headers["content-length"]?.toIntOrNull()?.let { if (it <= body.size) body = body.copyOf(it) }
        }
        return HttpResult(status, String(body, Charsets.UTF_8))
    }

    private fun dechunk(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        var pos = 0
        while (pos < data.size) {
            val lineEnd = indexOf(data, "\r\n".toByteArray(), pos)
            if (lineEnd < 0) break
            val size = String(data, pos, lineEnd - pos, Charsets.ISO_8859_1).substringBefore(';').trim().toInt(16)
            if (size == 0) break
            val start = lineEnd + 2
            out.write(data, start, minOf(size, data.size - start))
            pos = start + size + 2
        }
        return out.toByteArray()
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray, from: Int): Int {
        outer@ for (i in from..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }

    /** RFC 6125：按证书的 DNS 类型 SAN 匹配，通配符只匹配最左侧一级 */
    internal fun hostnameMatches(cert: X509Certificate, host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        val names = cert.subjectAlternativeNames?.filter { it[0] == 2 }?.map { (it[1] as String).lowercase().trimEnd('.') }.orEmpty()
        return names.any { name ->
            if (name.startsWith("*.")) {
                val suffix = name.substring(1)
                h.endsWith(suffix) && h.length > suffix.length && h.substring(0, h.length - suffix.length).indexOf('.') < 0
            } else {
                name == h
            }
        }
    }
}
