package app.octosms.commoncrypto.crypto

import app.octosms.commoncrypto.model.EncryptionType

object CryptoServiceFactory {

    fun create(type: EncryptionType): CryptoService {
        return when (type) {
            EncryptionType.AES_GCM -> AesGcmCryptoService()
            // 将来可以添加新的加密算法实现
            // EncryptionType.CHACHA20_POLY1305 -> ChaCha20CryptoService()
        }
    }
}
