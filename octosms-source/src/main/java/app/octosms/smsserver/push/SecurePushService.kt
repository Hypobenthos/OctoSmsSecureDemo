package app.octosms.smsserver.push

import android.content.ContentValues
import android.content.Context
import androidx.core.net.toUri
import app.octosms.commoncrypto.crypto.CryptoServiceFactory
import app.octosms.commoncrypto.error.PushResultCode
import app.octosms.commoncrypto.key.ContentKeyFetcher
import app.octosms.commoncrypto.log.logE
import app.octosms.commoncrypto.log.logI
import app.octosms.commoncrypto.log.logW
import app.octosms.commoncrypto.model.CryptoResult
import app.octosms.commoncrypto.model.EncryptedData
import app.octosms.commoncrypto.model.EncryptionType
import app.octosms.commoncrypto.model.SmsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object SecurePushService : SmsPushService {

    private const val TAG = "SecurePushService"

    private var encryptionType = EncryptionType.AES_GCM
    var appClient = "app.octosms.smsclient"
    var contentPath = "smsprovider/sms_data"
    var pushTimeoutMs: Long = 5_000

    override suspend fun push(context: Context, smsData: SmsData) {
        pushToAppB(context, smsData)
    }

    private suspend fun pushToAppB(context: Context, smsData: SmsData) {
        withContext(Dispatchers.IO) {
            if (!validateSmsData(smsData)) {
                "Push aborted: invalid SMS data.".logW(TAG)
                return@withContext
            }

            val keyResult = ContentKeyFetcher.fetchKeyIfNeeded(context)
            if (keyResult.isFailure) {
                "Push aborted: failed to fetch encryption key. Cause=${keyResult.exceptionOrNull()?.message}".logE(
                    TAG
                )
                return@withContext
            }

            val key = keyResult.getOrNull()
            if (key.isNullOrBlank()) {
                "Push aborted: encryption key is null or blank.".logE(TAG)
                return@withContext
            }
            val encResult = CryptoServiceFactory.create(encryptionType).encrypt(smsData, key)
            if (encResult is CryptoResult.Failure) {
                "Push aborted: encryption failed. Reason=${encResult.error}".logE(TAG)
                return@withContext
            }

            val encryptedData = (encResult as CryptoResult.Success).data

            val resultCode = withTimeoutOrNull(pushTimeoutMs) {
                pushViaContentProvider(context, encryptedData)
            } ?: PushResultCode.UNKNOWN

            if (resultCode == PushResultCode.SUCCESS) {
                "Push successful.".logI(TAG)
            } else {
                "Push failed with code=${resultCode.code}, desc=${resultCode.description}".logW(TAG)
                if (resultCode.retryable) {
                    ContentKeyFetcher.clearKey()
                    retryPush(context, smsData)
                }
            }
        }
    }


    private fun pushViaContentProvider(
        context: Context,
        encryptedData: EncryptedData
    ): PushResultCode {
        return try {
            val uri = "content://$appClient.$contentPath".toUri()
            val values = ContentValues().apply {
                put("encrypted_data", encryptedData.data)
                put("iv", encryptedData.iv)
                put("timestamp", encryptedData.timestamp)
                put("checksum", encryptedData.checksum)
                put("version", encryptedData.version)
                put("algorithm", encryptedData.algorithm.tag)
                put("data_type", "sms")
                put("sender_app", context.packageName)
            }
            val resultUri = context.contentResolver.insert(uri, values)
            PushResultCode.fromCode(resultUri?.lastPathSegment)
        } catch (e: Exception) {
            "PushViaContentProvider error: ${e.message}".logE(TAG, e)
            PushResultCode.UNKNOWN
        }
    }


    private suspend fun retryPush(context: Context, smsData: SmsData) {
        val delayMs = 1500L // 重试前的等待时间
        kotlinx.coroutines.delay(delayMs)

        "Retry attempt 1/1.".logI(TAG)

        val keyResult = ContentKeyFetcher.fetchKeyIfNeeded(context)
        if (keyResult.isFailure) {
            "Retry aborted: failed to fetch key. Cause=${keyResult.exceptionOrNull()?.message}".logW(
                TAG
            )
            return
        }

        val key = keyResult.getOrNull()
        if (key.isNullOrBlank()) {
            "Retry aborted: key is null or blank.".logE(TAG)
            return
        }

        val encResult = CryptoServiceFactory.create(encryptionType).encrypt(smsData, key)
        if (encResult is CryptoResult.Failure) {
            "Retry aborted: encryption failed. Reason=${encResult.error}".logE(TAG)
            return
        }

        val encryptedData = (encResult as CryptoResult.Success).data
        val resultCode = pushViaContentProvider(context, encryptedData)

        if (resultCode == PushResultCode.SUCCESS) {
            "Retry push successful.".logI(TAG)
        } else {
            "Retry failed with code=${resultCode.code}, desc=${resultCode.description}".logE(TAG)
        }
    }

    private fun validateSmsData(smsData: SmsData): Boolean {
        if (smsData.message.isBlank()) {
            "SMS data validation failed: message is blank.".logW(TAG)
            return false
        }
        if (smsData.message.length > 50_000) {
            "SMS data validation failed: message too long (${smsData.message.length}).".logW(TAG)
            return false
        }
        return true
    }
}
