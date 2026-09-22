package com.example.mobile.network

import com.example.mobile.BuildConfig

object NetworkEndpointResolver {
    @Volatile
    var resolvedBaseUrl: String? = null

    val candidateBaseUrls: List<String> = listOf(
        BuildConfig.API_BASE_URL,
        "http://localhost:3000/api/v1/",
        "http://10.0.2.2:3000/api/v1/",
        "http://192.168.0.8:3000/api/v1/",
        "http://10.0.2.2:80/api/v1/"
    ).distinct()
}
