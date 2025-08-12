package app.octosms.commoncrypto.model

enum class EncryptionType(val tag: String) {
    AES_GCM("aes_gcm");

    companion object {
        fun fromString(value: String?): EncryptionType =
            values().find { it.tag.equals(value, ignoreCase = true) } ?: AES_GCM
    }
}