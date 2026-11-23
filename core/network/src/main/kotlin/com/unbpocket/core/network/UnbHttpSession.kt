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
import java.util.concurrent.TimeUnit

/**
 * Interceptor OkHttp que aplica o guard-rail Read-Only antes de cada chamada de rede.
 */
class SigaaReadOnlyInterceptor internal constructor(
    private val allowedHosts: Set<String>,
) : Interceptor {
    constructor() : this(SigaaReadOnlyGuard.defaultAllowedHosts)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bodyText = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        }
        SigaaReadOnlyGuard.validateRequestWithHosts(
            host = request.url.host,
            path = request.url.encodedPath,
            method = request.method,
            formEncodedBody = bodyText,
            allowedHosts = allowedHosts,
        )
        return chain.proceed(request)
    }
}

/**
 * CookieJar em memoria compativel com cookies de dominio (.unb.br) e host.
 */
class InMemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        synchronized(this.cookies) {
            this.cookies.removeAll { existing ->
                existing.expiresAt < now || cookies.any {
                    it.name == existing.name && it.domain == existing.domain && it.path == existing.path
                }
            }
            this.cookies.addAll(cookies.filter { it.expiresAt >= now })
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(this.cookies) {
            this.cookies.removeAll { it.expiresAt < now }
            return this.cookies.filter { it.matches(url) }
        }
    }

    fun clear() {
        synchronized(this.cookies) {
            this.cookies.clear()
        }
    }
}

/**
 * Serializa chamadas JSF que dependem do estado sequencial `javax.faces.ViewState`
 * e expoe o cliente OkHttp configurado para o SIGAA e RU.
 */
class UnbHttpSession(
    val cookieJar: InMemoryCookieJar = InMemoryCookieJar(),
    val sigaaClient: OkHttpClient = defaultClient(cookieJar, followRedirects = true, SigaaReadOnlyGuard.defaultAllowedHosts),
    val noRedirectClient: OkHttpClient = defaultClient(cookieJar, followRedirects = false, SigaaReadOnlyGuard.defaultAllowedHosts),
) {
    private val jsfMutex = Mutex()

    suspend fun <T> withJsfLock(block: suspend () -> T): T = jsfMutex.withLock { block() }

    companion object {
        val SIGAA_CHARSET: Charset = Charset.forName("ISO-8859-1")

        fun decodeSigaaBytes(bytes: ByteArray): String = String(bytes, SIGAA_CHARSET)

        internal fun forTesting(allowedHosts: Set<String>): UnbHttpSession {
            val jar = InMemoryCookieJar()
            return UnbHttpSession(
                cookieJar = jar,
                sigaaClient = defaultClient(jar, followRedirects = true, allowedHosts),
                noRedirectClient = defaultClient(jar, followRedirects = false, allowedHosts),
            )
        }

        private fun defaultClient(
            cookieJar: CookieJar,
            followRedirects: Boolean,
            allowedHosts: Set<String>,
        ): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(followRedirects)
                .followSslRedirects(followRedirects)
                .addInterceptor(SigaaReadOnlyInterceptor(allowedHosts))
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
    }
}
