package com.wantox86.signpdf.data.remote

import com.wantox86.signpdf.data.remote.dto.CreateSignatureRequest
import com.wantox86.signpdf.data.remote.dto.LoginRequest
import com.wantox86.signpdf.data.remote.dto.LoginResponse
import com.wantox86.signpdf.data.remote.dto.SignatureDto
import com.wantox86.signpdf.data.remote.dto.SignatureListResponse
import com.wantox86.signpdf.data.remote.dto.UpdateSignatureRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

// Registration is intentionally omitted here: per spec, account provisioning is an
// out-of-band admin action (REGISTRATION_ENABLED flag-gated server-side), no client UI
// ever calls POST /api/auth/register.
interface SignPdfApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    // Response<Void>, not Unit: the backend replies 200 with an empty body for logout, and
    // Retrofit special-cases Void to skip response-body conversion entirely -- decoding an
    // empty body as Unit via the kotlinx.serialization converter would throw.
    @POST("api/auth/logout")
    suspend fun logout(): Response<Void>

    @GET("api/signatures")
    suspend fun listSignatures(): SignatureListResponse

    @POST("api/signatures")
    suspend fun createSignature(@Body request: CreateSignatureRequest): SignatureDto

    @PUT("api/signatures/{id}")
    suspend fun updateSignature(
        @Path("id") id: Long,
        @Body request: UpdateSignatureRequest,
    ): SignatureDto

    @DELETE("api/signatures/{id}")
    suspend fun deleteSignature(@Path("id") id: Long): Response<Void>
}
