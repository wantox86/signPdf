package com.wantox86.signpdf.data.remote

import com.wantox86.signpdf.data.local.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

// Attaches "Authorization: Basic <opaque-token>" -- NOT RFC7617 Basic auth, see
// signPDF-Backend's README. Only attaches when a token is actually stored; login itself
// doesn't need one, and Guest mode never has one, so this interceptor is a no-op for both.
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenStore.token() ?: return chain.proceed(request)
        val authenticated = request.newBuilder()
            .header("Authorization", "Basic $token")
            .build()
        return chain.proceed(authenticated)
    }
}
