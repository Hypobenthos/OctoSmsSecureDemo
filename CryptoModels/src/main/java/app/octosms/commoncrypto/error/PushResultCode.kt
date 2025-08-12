package app.octosms.commoncrypto.error


enum class PushResultCode(val code: String, val description: String, val retryable: Boolean) {
    SUCCESS("success", "Push successful", false),

    // key 相关错误
    INVALID_KEY("error:invalid_key", "Invalid encryption key", true),
    KEY_NOT_FOUND("error:key_not_found", "Encryption key not found", true),

    // 数据相关错误
    INVALID_DATA("error:invalid_data", "Invalid or missing data", false),
    DECRYPTION_FAILED("error:decryption_failed", "Failed to decrypt data", false),

    // 其他错误
    UNKNOWN("error:unknown", "Unknown error", false);

    companion object {
        fun fromCode(code: String?): PushResultCode {
            return values().find { it.code == code } ?: UNKNOWN
        }
    }
}
