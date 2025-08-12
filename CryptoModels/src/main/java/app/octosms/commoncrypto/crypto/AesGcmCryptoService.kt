package app.octosms.commoncrypto.crypto

import android.util.Base64
import app.octosms.commoncrypto.log.logD
import app.octosms.commoncrypto.model.EncryptionType
import app.octosms.commoncrypto.model.CryptoError
import app.octosms.commoncrypto.model.CryptoResult
import app.octosms.commoncrypto.model.EncryptedData
import app.octosms.commoncrypto.model.SmsData
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AesGcmCryptoService : CryptoService {

    companion object {
        private const val AES_KEY_LENGTH = 32
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val ALGORITHM = "AES/GCM/NoPadding"
    }

    private val gson: Gson =
        GsonBuilder()
            .disableHtmlEscaping()
            .setDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
            .create()

    override fun encrypt(smsData: SmsData, key: String): CryptoResult<EncryptedData> {
        return try {
            if (key.length != AES_KEY_LENGTH) return CryptoResult.Failure(CryptoError.InvalidKey)

            val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
            val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
            val cipher = Cipher.getInstance(ALGORITHM)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)

            val jsonData = gson.toJson(smsData)
            val encryptedBytes = cipher.doFinal(jsonData.toByteArray(Charsets.UTF_8))
            val checksum = CryptoUtils.generateChecksum(jsonData)

            CryptoResult.Success(
                EncryptedData(
                    data = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
                    iv = Base64.encodeToString(iv, Base64.NO_WRAP),
                    timestamp = System.currentTimeMillis(),
                    checksum = checksum,
                    version = smsData.dataVersion,
                    algorithm = EncryptionType.AES_GCM
                )
            )
        } catch (e: Exception) {
            CryptoResult.Failure(
                CryptoError.EncryptionFailed(
                    e.message ?: "Unknown encryption error"
                )
            )
        }
    }

    override fun decrypt(
        encryptedData: EncryptedData,
        key: String
    ): CryptoResult<SmsData> {
        return try {
            // 1. 算法不支持
            if (encryptedData.algorithm != EncryptionType.AES_GCM) {
                return CryptoResult.Failure(CryptoError.UnsupportedAlgorithm)
            }

            // 2. Key 长度错误
            if (key.length != AES_KEY_LENGTH) {
                return CryptoResult.Failure(CryptoError.InvalidKey)
            }

            // 3. 初始化解密
            val keySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
            val cipher = Cipher.getInstance(ALGORITHM)
            val ivBytes = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(encryptedData.data, Base64.NO_WRAP)
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, ivBytes)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)

            // 4. 执行解密
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val jsonData = String(decryptedBytes, Charsets.UTF_8)

            // 5. 校验 checksum
            if (CryptoUtils.generateChecksum(jsonData) != encryptedData.checksum) {
                return CryptoResult.Failure(CryptoError.ChecksumMismatch)
            }

            // 6. JSON 反序列化
            val smsData = gson.fromJson(jsonData, SmsData::class.java)
            CryptoResult.Success(smsData)
        } catch (e: Exception) {
            if (e is AEADBadTagException) {
                return CryptoResult.Failure(CryptoError.InvalidKey)
            }

            CryptoResult.Failure(
                CryptoError.DecryptionFailed(e.message ?: "Unknown decryption error")
            )
        }
    }


    override fun getSupportedAlgorithm(): EncryptionType = EncryptionType.AES_GCM
}
