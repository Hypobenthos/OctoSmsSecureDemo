package app.octosms.commoncrypto.model

import app.octosms.commoncrypto.model.EncryptionType


data class SmsData(
    val sender: String,
    val message: String,
    val timestamp: Long,
    val source: String = "app_a",
    val appVersion: String = "1.0",
    val encryptionTime: Long = System.currentTimeMillis(),
    val messageLength: Int = message.length,
    val dataVersion: String = "1.0"
)

data class EncryptedData(
    val data: String,     // Base64 ciphertext
    val iv: String,       // Base64 IV
    val timestamp: Long,  // encryption time
    val checksum: String, // SHA-256 checksum of plaintext JSON
    val version: String,  // data version
    val algorithm: EncryptionType
)

