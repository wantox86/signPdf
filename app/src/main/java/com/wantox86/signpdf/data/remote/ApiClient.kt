package com.wantox86.signpdf.data.remote

import com.wantox86.signpdf.BuildConfig
import com.wantox86.signpdf.data.local.TokenStorage
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun create(tokenStore: TokenStorage): SignPdfApiService {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenStore))
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(SignPdfApiService::class.java)
    }
}
