package app.octosms.commoncrypto.crypto

import app.octosms.commoncrypto.model.EncryptionType
import app.octosms.commoncrypto.model.CryptoResult
import app.octosms.commoncrypto.model.EncryptedData
import app.octosms.commoncrypto.model.SmsData

interface CryptoService {
    fun encrypt(smsData: SmsData, key: String): CryptoResult<EncryptedData>
    fun decrypt(encryptedData: EncryptedData, key: String): CryptoResult<SmsData>
    fun getSupportedAlgorithm(): EncryptionType
}
