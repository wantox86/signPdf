package com.wantox86.signpdf.data.remote

import com.wantox86.signpdf.data.remote.dto.CreateSignatureRequest
import com.wantox86.signpdf.data.remote.dto.LoginRequest
import com.wantox86.signpdf.data.remote.dto.LoginResponse
import com.wantox86.signpdf.data.remote.dto.SignatureDto
import com.wantox86.signpdf.data.remote.dto.SignatureListResponse
import com.wantox86.signpdf.data.remote.dto.UpdateSignatureRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant

// In-memory stand-in for the real backend, used by SyncRepositoryTest to exercise the sync
// algorithm's branches without a network or a running signPDF-Backend instance.
class FakeSignPdfApiService : SignPdfApiService {
    val signatures = mutableListOf<SignatureDto>()
    var listCallCount = 0
    var throwOnList: Exception? = null

    // Deterministic, monotonically increasing "server clock" so createdAt/updatedAt ordering
    // is meaningful across calls within a test.
    private var clockSeconds = 0L
    private fun nextTimestamp(): String = Instant.EPOCH.plusSeconds(clockSeconds++).toString()

    override suspend fun login(request: LoginRequest): LoginResponse {
        throw NotImplementedError("not used by SyncRepository")
    }

    override suspend fun logout(): Response<Void> = Response.success(null)

    override suspend fun listSignatures(): SignatureListResponse {
        listCallCount++
        throwOnList?.let { throw it }
        return SignatureListResponse(
            signatures = signatures.filter { it.type == "SIGNATURE" },
            initials = signatures.filter { it.type == "INITIAL" },
        )
    }

    override suspend fun createSignature(request: CreateSignatureRequest): SignatureDto {
        val dto = SignatureDto(
            id = (signatures.maxOfOrNull { it.id } ?: 0L) + 1,
            type = request.type,
            name = request.name,
            data = request.data,
            createdAt = nextTimestamp(),
            updatedAt = nextTimestamp(),
        )
        signatures.add(dto)
        return dto
    }

    override suspend fun updateSignature(id: Long, request: UpdateSignatureRequest): SignatureDto {
        val index = signatures.indexOfFirst { it.id == id }
        check(index >= 0) { "no fake signature with id $id" }
        val updated = signatures[index].copy(
            name = request.name,
            data = request.data,
            updatedAt = nextTimestamp(),
        )
        signatures[index] = updated
        return updated
    }

    override suspend fun deleteSignature(id: Long): Response<Void> {
        signatures.removeIf { it.id == id }
        return Response.success(null)
    }

    // Sets updatedAt/data directly, simulating a change made from another device that this
    // client's local file/metadata knows nothing about.
    fun simulateRemoteEdit(id: Long, newData: String) {
        val index = signatures.indexOfFirst { it.id == id }
        check(index >= 0) { "no fake signature with id $id" }
        signatures[index] = signatures[index].copy(data = newData, updatedAt = nextTimestamp())
    }

    companion object {
        fun unauthorized(): HttpException {
            val body = "{\"error\":{\"code\":\"SESSION_EXPIRED\",\"message\":\"Session expired\"}}"
                .toResponseBody("application/json".toMediaType())
            return HttpException(Response.error<Any>(401, body))
        }
    }
}
