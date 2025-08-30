package app.octosms.smsclient.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import app.octosms.commoncrypto.callback.SmsDataCallbackManager
import app.octosms.commoncrypto.config.SecurityConfigManager
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
import java.security.MessageDigest

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
        val config = SecurityConfigManager.getConfig()
        if (config.isDebugMode()) {
            debugPrintSignatureFingerprints()
        }
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

            // 使用实际的调用方包名进行验证，而不是 ContentValues 中的值
            val actualCallingPackage = getCallingPackageName()

            // 验证 ContentValues 中的 sender_app 与实际调用方是否一致
            if (actualCallingPackage != senderApp) {
                "Sender app mismatch. Claimed: $senderApp, Actual: $actualCallingPackage".logW(TAG)
                return null
            }

            if (!isTrustedCaller(actualCallingPackage)) {
                "Unauthorized access from: $actualCallingPackage".logW(TAG)
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

    /**
     * 获取调用方包名，添加错误处理
     */
    private fun getCallingPackageName(): String? {
        return try {
            val pm = context?.packageManager ?: return null
            val uid = Binder.getCallingUid()
            pm.getPackagesForUid(uid)?.firstOrNull()
        } catch (e: Exception) {
            "Error getting calling package name: ${e.message}".logW(TAG)
            null
        }
    }

    /**
     * 验证调用方是否可信（使用可配置的安全策略）
     */
    private fun isTrustedCaller(pkg: String?): Boolean {
        if (pkg == null) return false

        val context = context ?: return false
        val config = SecurityConfigManager.getConfig()

        return try {
            // 1. 检查是否启用了包名白名单
            if (config.isPackageWhitelistEnabled()) {
                if (!isAllowedPackage(pkg)) {
                    "Package not in whitelist: $pkg".logW(TAG)
                    return false
                }
            }

            // 2. 检查是否启用了签名指纹验证
            if (config.isFingerprintVerificationEnabled()) {
                val expectedFingerprints = config.getExpectedFingerprints(pkg)
                if (expectedFingerprints.isEmpty()) {
                    "No expected fingerprints for package: $pkg".logW(TAG)
                    return false
                }

                val actualFingerprint = getPackageSignatureFingerprint(context.packageManager, pkg)
                if (actualFingerprint == null) {
                    "Cannot get signature fingerprint for package: $pkg".logW(TAG)
                    return false
                }

                // 检查实际指纹是否匹配任何一个预期指纹
                val isMatch = expectedFingerprints.any { expected ->
                    expected.equals(actualFingerprint, ignoreCase = true)
                }

                if (isMatch) {
                    "Trusted caller verified: $pkg with fingerprint: $actualFingerprint".logD(TAG)
                } else {
                    "Signature mismatch for $pkg. Expected: $expectedFingerprints, Actual: $actualFingerprint".logW(TAG)
                }

                return isMatch
            }

            // 如果两个验证都没启用，则允许访问（不推荐在生产环境中使用）
            if (!config.isPackageWhitelistEnabled() && !config.isFingerprintVerificationEnabled()) {
                "Warning: No security verification enabled!".logW(TAG)
                return true
            }

            true

        } catch (e: PackageManager.NameNotFoundException) {
            "Package not found: $pkg".logW(TAG)
            false
        } catch (e: Exception) {
            "Error verifying caller trust: ${e.message}".logW(TAG)
            false
        }
    }

    /**
     * 检查包名是否在允许列表中
     */
    private fun isAllowedPackage(packageName: String): Boolean {
        val config = SecurityConfigManager.getConfig()
        return packageName in config.getAllowedPackages()
    }

    /**
     * 获取包的签名 SHA1 指纹
     */
    private fun getPackageSignatureFingerprint(pm: PackageManager, packageName: String): String? {
        return try {
            val signatures = getPackageSignatures(pm, packageName)
            if (signatures == null || signatures.isEmpty()) {
                return null
            }

            // 获取第一个签名的 SHA1 指纹
            val signature = signatures[0]
            val digest = MessageDigest.getInstance("SHA1")
            val fingerprint = digest.digest(signature.toByteArray())

            fingerprint.joinToString(":") {
                String.format("%02X", it)
            }

        } catch (e: Exception) {
            "Error getting signature fingerprint for $packageName: ${e.message}".logE(TAG)
            null
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

    /**
     * 调试用：获取并打印所有相关应用的签名指纹
     */
    private fun debugPrintSignatureFingerprints() {
        val context = context ?: return
        val pm = context.packageManager
        val config = SecurityConfigManager.getConfig()

        val packagesToCheck = mutableListOf<String>().apply {
            add(context.packageName) // 当前应用
            addAll(config.getAllowedPackages()) // 配置中的所有包名
        }

        "=== SMS Provider Signature Fingerprints (Debug Mode) ===".logD(TAG)

        packagesToCheck.forEach { pkg ->
            try {
                val fingerprint = getPackageSignatureFingerprint(pm, pkg)
                "$pkg: $fingerprint".logD(TAG)
            } catch (e: Exception) {
                "$pkg: Error - ${e.message}".logE(TAG)
            }
        }

        "=== End SMS Provider Fingerprints ===".logD(TAG)
    }

    /**
     * 原有的签名比较方法（现在不再使用，但保留以备后用）
     */
    @Deprecated("Use fingerprint verification instead")
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