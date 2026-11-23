package com.unbpocket.core.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Interceptor OkHttp que aplica o guard-rail Read-Only antes de cada chamada de rede.
 */
class SigaaReadOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bodyText = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }
        SigaaReadOnlyGuard.validateRequest(
            host = request.url.host,
            path = request.url.encodedPath,
            method = request.method,
            formEncodedBody = bodyText,
        )
        return chain.proceed(request)
    }
}

/**
 * CookieJar em memoria isolado por dominio para o fluxo CAS -> SIG -> SIGAA.
 */
class InMemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val hostCookies = store.getOrPut(url.host) { mutableListOf() }
        synchronized(hostCookies) {
            val now = System.currentTimeMillis()
            hostCookies.removeAll { existing ->
                existing.expiresAt < now || cookies.any { it.name == existing.name && it.path == existing.path }
            }
            hostCookies.addAll(cookies.filter { it.expiresAt >= now })
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        synchronized(hostCookies) {
            hostCookies.removeAll { it.expiresAt < now }
            return hostCookies.filter { it.matches(url) }
        }
    }

    fun clear() {
        store.clear()
    }
}

/**
 * Serializa chamadas JSF que dependem do estado sequencial `javax.faces.ViewState`
 * e expoe o cliente OkHttp configurado para o SIGAA e RU.
 */
class UnbHttpSession(
    val cookieJar: InMemoryCookieJar = InMemoryCookieJar(),
    val sigaaClient: OkHttpClient = defaultClient(cookieJar, followRedirects = true),
    val noRedirectClient: OkHttpClient = defaultClient(cookieJar, followRedirects = false),
) {
    private val jsfMutex = Mutex()

    suspend fun <T> withJsfLock(block: suspend () -> T): T = jsfMutex.withLock { block() }

    companion object {
        val SIGAA_CHARSET: Charset = Charset.forName("ISO-8859-1")

        fun decodeSigaaBytes(bytes: ByteArray): String = String(bytes, SIGAA_CHARSET)

        private fun defaultClient(cookieJar: CookieJar, followRedirects: Boolean): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .addInterceptor(SigaaReadOnlyInterceptor())
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
