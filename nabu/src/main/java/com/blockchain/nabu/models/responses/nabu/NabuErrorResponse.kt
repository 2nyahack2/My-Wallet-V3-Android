package com.blockchain.nabu.models.responses.nabu

import android.annotation.SuppressLint
import com.squareup.moshi.Moshi
import retrofit2.HttpException

private data class NabuErrorResponse(
    /**
     * Machine-readable error code.
     */
    val code: Int,
    /**
     * Machine-readable error type./ο-
     */
    val type: String,
    /**
     * Human-readable error description.
     */
    val description: String
)

class NabuApiException private constructor(message: String) : Throwable(message) {

    private var _httpErrorCode: Int = -1
    private var _errorCode: Int = -1
    private lateinit var _error: String
    private lateinit var _errorDescription: String

    fun getErrorCode(): NabuErrorCodes = NabuErrorCodes.fromErrorCode(_errorCode)

    fun getErrorStatusCode(): NabuErrorStatusCodes = NabuErrorStatusCodes.fromErrorCode(_httpErrorCode)

    fun getErrorType(): NabuErrorTypes = NabuErrorTypes.fromErrorStatus(_error)

    /**
     * Returns a human-readable error message.
     */
    fun getErrorDescription(): String = _errorDescription

    companion object {
        @SuppressLint("SyntheticAccessor")
        fun fromResponseBody(exception: Throwable?): NabuApiException {
            val moshi = Moshi.Builder().build()
            val adapter = moshi.adapter(NabuErrorResponse::class.java)
            return if (exception is HttpException) {
                exception.response()?.errorBody()?.string()?.let { errorBody ->
                    val errorResponse = adapter.fromJson(errorBody)
                    errorResponse?.let {
                        val httpErrorCode = exception.code()
                        val error = it.type
                        val errorDescription = it.description
                        val errorCode = it.code
                        val path = exception.response()?.raw()?.request?.url?.pathSegments?.joinToString(" , ")

                        NabuApiException("$httpErrorCode: $error - $errorDescription - $errorCode - $path")
                            .apply {
                                _httpErrorCode = httpErrorCode
                                _error = error
                                _errorCode = errorCode
                                _errorDescription = errorDescription
                            }
                    }
                } ?: NabuApiException(exception.message())
            } else {
                NabuApiException(exception?.message ?: "Unknown exception")
            }
        }

        fun withErrorCode(errorCode: Int): NabuApiException {
            return NabuApiException("")
                .apply {
                    _errorCode = errorCode
                }
        }
    }
}