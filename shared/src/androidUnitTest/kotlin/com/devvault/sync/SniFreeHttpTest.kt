package com.devvault.sync

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 访问真实服务器的只读接口，默认跳过：DEVVAULT_NET_TEST=1 ./gradlew :shared:testDebugUnitTest
 */
class SniFreeHttpTest {
    private val enabled = System.getenv("DEVVAULT_NET_TEST") == "1"

    @Test
    fun serverInfoWithoutSni() = runBlocking {
        if (!enabled) return@runBlocking
        val res = SniFreeHttp.request("https://vault.749log.com/api/server/info", "GET", emptyMap(), null, 15_000)
        println("status=${res.status} body=${res.body}")
        assertEquals(200, res.status)
        assertTrue("\"apiVersion\"" in res.body)
    }

    @Test
    fun cloudHttpPicksCompatibilityRoute() = runBlocking {
        if (!enabled) return@runBlocking
        val res = CloudHttp().send("https://vault.749log.com/api/server/info", io.ktor.http.HttpMethod.Get, null, null)
        println("status=${res.status} body=${res.body}")
        assertEquals(200, res.status)
    }
}
