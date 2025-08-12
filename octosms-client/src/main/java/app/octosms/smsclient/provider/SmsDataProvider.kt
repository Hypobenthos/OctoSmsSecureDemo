package app.octosms.smsclient.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.net.Uri
import android.os.Build
import app.octosms.commoncrypto.callback.SmsDataCallbackManager
import app.octosms.commoncrypto.config.SmsSourceConfig
import app.octosms.commoncrypto.crypto.CryptoServiceFactory
import app.octosms.commoncrypto.error.PushResultCode
import app.octosms.commoncrypto.key.SharedKeyManager
import app.octosms.commoncrypto.log.logD
import app.octosms.commoncrypto.log.logE
import app.octosms.commoncrypto.log.logI
import app.octosms.commoncrypto.log.logW
import app.octosms.commoncrypto.model.CryptoError
import app.octosms.commoncrypto.model.CryptoResult
import app.octosms.commoncrypto.model.EncryptedData
import app.octosms.commoncrypto.model.EncryptionType
import app.octosms.commoncrypto.model.SmsData

/**
 * Content Provider for receiving encrypted SMS data from authorized applications
 */
class SmsDataProvider : ContentProvider() {
    companion object {
        private const val TAG = "SmsDataProvider"
        private const val AUTHORITY = "app.octosms.smsclient.smsprovider"
        private const val SMS_DATA_CODE = 1
        private const val MAX_MESSAGE_LOG_LENGTH = 50


        // Required fields for encryption
        private val REQUIRED_ENCRYPTION_FIELDS =
            setOf(
                "encrypted_data",
                "iv",
                "checksum",
                "sender_app",
            )

        private val uriMatcher =
            UriMatcher(UriMatcher.NO_MATCH).apply {
                addURI(AUTHORITY, "sms_data", SMS_DATA_CODE)
            }
    }


    override fun onCreate(): Boolean {
        "SmsDataProvider initialized".logD(TAG)
        return true
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? =
        when (uriMatcher.match(uri)) {
            SMS_DATA_CODE -> handleSmsDataInsertion(values, uri)
            else -> {
                "Unsupported URI: $uri".logW(TAG)
                null
            }
        }

    /**
     * Handle SMS data insertion with comprehensive validation and error handling
     */
    private fun handleSmsDataInsertion(values: ContentValues?, uri: Uri): Uri {
        val resultCode = try {
            when {
                !validateInput(values) -> PushResultCode.INVALID_DATA
                !isSmsSourceEnabled() -> PushResultCode.SUCCESS // 非错误，不阻断
                else -> {
                    val validatedData =
                        extractAndValidateData(values!!) ?: PushResultCode.INVALID_DATA
                    if (validatedData is PushResultCode) {
                        validatedData
                    } else {
                        processEncryptedSmsDataSync(validatedData as ValidatedSmsData)
                    }
                }
            }
        } catch (e: Exception) {
            "Error processing SMS data: ${e.message}".logE(TAG, e)
            PushResultCode.UNKNOWN
        }

        return Uri.withAppendedPath(uri, resultCode.code)
    }

    /**
     * 同步处理加密数据并返回错误码
     */
    private fun processEncryptedSmsDataSync(validatedData: ValidatedSmsData): PushResultCode {
        val result = decryptSmsData(validatedData.encryptedData)
        return when (result) {
            is CryptoResult.Success -> {
                processDecryptedSmsData(result.data, validatedData.senderApp)
                PushResultCode.SUCCESS
            }

            is CryptoResult.Failure -> {
                when (result.error) {
                    CryptoError.InvalidKey -> PushResultCode.INVALID_KEY
                    else -> PushResultCode.DECRYPTION_FAILED
                }
            }

            null -> PushResultCode.UNKNOWN
        }
    }

    /**
     * Validate basic input requirements
     */
    private fun validateInput(values: ContentValues?): Boolean {
        if (values == null) {
            "ContentValues is null".logE(TAG)
            return false
        }

        val missingFields =
            REQUIRED_ENCRYPTION_FIELDS.filter { field ->
                values.getAsString(field).isNullOrBlank()
            }

        if (missingFields.isNotEmpty()) {
            "Missing required fields: $missingFields".logE(TAG)
            return false
        }

        return true
    }

    /**
     * Check if SMS source is enabled with timeout protection
     */
    private fun isSmsSourceEnabled(): Boolean = SmsSourceConfig.isEnabled()

    /**
     * Extract and validate SMS data from ContentValues
     */
    private fun extractAndValidateData(values: ContentValues): ValidatedSmsData? {
        return try {
            val senderApp = values.getAsString("sender_app")!!
            if (!isTrustedCaller(senderApp)) {
                "Invalid sender app: $senderApp".logW(TAG)
                return null
            }

            val encryptedData = extractEncryptedData(values) ?: return null

            ValidatedSmsData(senderApp, encryptedData)
        } catch (e: Exception) {
            "Failed to extract and validate data: ${e.message}".logE(TAG)
            null
        }
    }

    /**
     * Data class to hold validated SMS data
     */
    private data class ValidatedSmsData(
        val senderApp: String,
        val encryptedData: EncryptedData,
    )

    /**
     * Extract encrypted data from ContentValues with enhanced validation
     */
    private fun extractEncryptedData(values: ContentValues): EncryptedData? =
        try {
            val data = values.getAsString("encrypted_data")!!
            val iv = values.getAsString("iv")!!
            val checksum = values.getAsString("checksum")!!
            val timestamp = values.getAsLong("timestamp") ?: System.currentTimeMillis()
            val version = values.getAsString("version") ?: "1.0"
            val algorithm = EncryptionType.fromString(values.getAsString("algorithm"))

            EncryptedData(
                data = data,
                iv = iv,
                timestamp = timestamp,
                checksum = checksum,
                version = version,
                algorithm = algorithm,
            )
        } catch (e: Exception) {
            "Failed to extract encrypted data: ${e.message}".logE(TAG)
            null
        }

    private fun isTrustedCaller(pkg: String): Boolean {
        val context = context ?: return false
        val pm = context.packageManager

        return try {
            val mySignatures = getPackageSignatures(pm, context.packageName)
            val otherSignatures = getPackageSignatures(pm, pkg)

            compareSignatures(mySignatures, otherSignatures)
        } catch (e: PackageManager.NameNotFoundException) {
            "Package not found: $pkg ${e.message}".logW(TAG)
            false
        } catch (e: Exception) {
            "Error verifying caller trust: ${e.message}".logW(TAG)
            false
        }
    }

    /**
     * 比较两个签名数组是否匹配
     */
    private fun compareSignatures(
        signatures1: Array<Signature>?,
        signatures2: Array<Signature>?,
    ): Boolean {
        if (signatures1 == null || signatures2 == null) return false

        return signatures1.any { sig1 ->
            signatures2.any { sig2 ->
                sig1.toCharsString() == sig2.toCharsString()
            }
        }
    }

    /**
     * 获取包的签名信息
     */
    private fun getPackageSignatures(
        pm: PackageManager,
        packageName: String,
    ): Array<Signature>? {
        val packageInfo =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }
    }


    private fun decryptSmsData(encryptedData: EncryptedData): CryptoResult<SmsData>? {
        val key = SharedKeyManager.getOrGenerateKey()
        if (key.isBlank()) {
            "Local key is null or blank, cannot decrypt".logE(TAG)
            return null
        }

        return try {
            "Decrypting data using ${encryptedData.algorithm} algorithm".logD(TAG)
            CryptoServiceFactory.create(encryptedData.algorithm).decrypt(encryptedData, key)
        } catch (e: Exception) {
            "Decryption failed: ${e.message}".logE(TAG, e)
            CryptoResult.Failure(CryptoError.InvalidKey)
        }
    }


    /**
     * Process decrypted SMS data with comprehensive logging
     */
    private fun processDecryptedSmsData(
        smsData: SmsData,
        senderApp: String,
    ) {
        try {
//            logSmsDataInfo(smsData, senderApp)
            SmsDataCallbackManager.notifyReceived(smsData, senderApp)
        } catch (e: Exception) {
            "Exception in processDecryptedSmsData: ${e.message}".logE(TAG)
        }
    }

    /**
     * Log SMS data information securely
     */
    private fun logSmsDataInfo(
        smsData: SmsData,
        senderApp: String,
    ) {
        "Processing SMS data from $senderApp".logI(TAG)
        "Sender: ${smsData.sender}".logD(TAG)
        "Message preview: ${smsData.message.take(MAX_MESSAGE_LOG_LENGTH)}${
            if (smsData.message.length > MAX_MESSAGE_LOG_LENGTH) "..." else ""
        }".logD(TAG)
        "Timestamp: ${smsData.timestamp}".logD(TAG)
        "Data version: ${smsData.dataVersion}".logD(TAG)
    }

    // ContentProvider interface methods (unused but required)
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        "query() called but not supported".logD(TAG)
        return null
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        "update() called but not supported".logD(TAG)
        return 0
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int {
        "delete() called but not supported".logD(TAG)
        return 0
    }

    override fun getType(uri: Uri): String? {
        "getType() called but not supported".logD(TAG)
        return null
    }
}
