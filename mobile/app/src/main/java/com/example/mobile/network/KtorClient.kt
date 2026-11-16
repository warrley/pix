package com.example.mobile.network

import com.example.mobile.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClient {
    // Reduced timeout to 5 seconds as requested
    private const val TIMEOUT_MILLIS = 5_000L

    @Volatile
    var authToken: String? = null

    fun createHttpClient(engine: HttpClientEngine = Android.create()): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(HttpTimeout) {
                requestTimeoutMillis = TIMEOUT_MILLIS
                connectTimeoutMillis = TIMEOUT_MILLIS
                socketTimeoutMillis = TIMEOUT_MILLIS
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = if (BuildConfig.DEBUG) LogLevel.INFO else LogLevel.NONE
                sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
            }

            defaultRequest {
                url(BuildConfig.API_BASE_URL)
                contentType(ContentType.Application.Json)
                authToken?.takeIf { it.isNotBlank() }?.let { token ->
                    headers.append(HttpHeaders.Authorization, "Bearer $token")
                }
            }

            engine {
                if (this is io.ktor.client.engine.android.AndroidEngineConfig) {
                    connectTimeout = TIMEOUT_MILLIS.toInt()
                    socketTimeout = TIMEOUT_MILLIS.toInt()
                }
            }
        }
    }

    val httpClient: HttpClient by lazy {
        createHttpClient()
    }
}
