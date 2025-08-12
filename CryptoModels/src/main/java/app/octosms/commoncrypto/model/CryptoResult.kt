package app.octosms.commoncrypto.model

sealed class CryptoResult<out T> {
    data class Success<T>(val data: T) : CryptoResult<T>()
    data class Failure(val error: CryptoError) : CryptoResult<Nothing>()
}

sealed class CryptoError(val message: String, val cause: Throwable? = null) {
    object InvalidKey : CryptoError("Invalid encryption key")
    object UnsupportedAlgorithm : CryptoError("Unsupported encryption algorithm")
    object ChecksumMismatch : CryptoError("Checksum mismatch")
    data class DecryptionFailed(val details: String) : CryptoError(details)
    data class EncryptionFailed(val details: String) : CryptoError(details)
}
