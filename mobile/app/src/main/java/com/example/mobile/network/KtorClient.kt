package com.example.mobile.network

import com.example.mobile.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.plugin
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClient {
    private const val TIMEOUT_MILLIS = 10_000L
    private const val LOGIN_PATH = "auth/token"

    fun create(
        sessionStore: SessionStore,
        engine: HttpClientEngine = Android.create(),
        baseUrl: String = BuildConfig.API_BASE_URL
    ): HttpClient {
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
                socketTimeoutMillis = TIMEOUT_MILLIS
            }

            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.INFO
                }
            }

            defaultRequest {
                url(baseUrl)
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                sessionStore.token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
        }

        // A 401 on any authenticated call means the token expired or was revoked.
        client.plugin(HttpSend).intercept { request ->
            val call = execute(request)
            val isLogin = request.url.encodedPath.endsWith(LOGIN_PATH)
            if (call.response.status == HttpStatusCode.Unauthorized && !isLogin && sessionStore.token != null) {
                sessionStore.end(expired = true)
            }
            call
        }
        return client
    }
}
