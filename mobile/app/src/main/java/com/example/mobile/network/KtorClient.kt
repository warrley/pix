package com.example.mobile.network

import com.example.mobile.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClient {
    private const val TIMEOUT = 30_000

    fun createHttpClient(engine: HttpClientEngine = Android.create()): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }

            defaultRequest {
                url(BuildConfig.API_BASE_URL)
                contentType(ContentType.Application.Json)
            }

            engine {
                if (this is io.ktor.client.engine.android.AndroidEngineConfig) {
                    connectTimeout = TIMEOUT
                    socketTimeout = TIMEOUT
                }
            }
        }
    }

    val httpClient: HttpClient by lazy {
        createHttpClient()
    }
}
