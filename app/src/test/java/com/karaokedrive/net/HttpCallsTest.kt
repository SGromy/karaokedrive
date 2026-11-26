package com.karaokedrive.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class HttpCallsTest {

    private val server = MockWebServer().apply { start() }
    private val http = OkHttpClient()

    @After
    fun tearDown() {
        server.close()
    }

    private fun call(path: String = "/") = http.newCall(Request.Builder().url(server.url(path)).build())

    @Test
    fun `renvoie la réponse du serveur`() = runTest {
        server.enqueue(MockResponse.Builder().body("La route chante").build())

        val body = call().await().use { it.body.string() }

        assertEquals("La route chante", body)
    }

    @Test
    fun `annuler la coroutine annule aussi la requête`() = runTest {
        server.enqueue(MockResponse.Builder().headersDelay(30, TimeUnit.SECONDS).build())
        val slow = call()

        val waiting = launch { slow.await() }
        delay(100)
        waiting.cancelAndJoin()

        assertTrue(slow.isCanceled())
    }

    @Test
    fun `un serveur injoignable remonte une IOException`() = runTest {
        val unreachable = call()
        server.close()

        val error = runCatching { unreachable.await() }.exceptionOrNull()

        assertTrue("attendu : IOException, obtenu : $error", error is IOException)
    }
}
