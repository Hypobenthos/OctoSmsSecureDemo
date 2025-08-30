package app.octosms.smsclient.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import app.octosms.commoncrypto.config.SecurityConfigManager
import app.octosms.commoncrypto.key.SharedKeyManager
import app.octosms.commoncrypto.log.logD
import app.octosms.commoncrypto.log.logW
import app.octosms.commoncrypto.log.logE
import java.security.MessageDigest

class KeyExchangeProvider : ContentProvider() {
    companion object {
        private const val COLUMN_KEY = "key"
        private const val LOG_TAG = "KeyExchangeProvider"

        // 使用 lazy 延迟初始化，线程安全
        private val secretKey: String by lazy {
            SharedKeyManager.getOrGenerateKey()
        }
    }

    override fun onCreate(): Boolean {
        val config = SecurityConfigManager.getConfig()
        if (config.isDebugMode()) {
            debugPrintSignatureFingerprints()
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val callingPackage = getCallingPackageName()
        "query() called by: $callingPackage".logD(LOG_TAG)

        if (callingPackage == null) {
            "Unable to determine calling package".logW(LOG_TAG)
            return null
        }

        if (!isTrustedCaller(callingPackage)) {
            "Unauthorized access from: $callingPackage".logW(LOG_TAG)
            return null
        }

        return createKeyResponse()
    }

    /**
     * 创建包含密钥的响应游标
     */
    private fun createKeyResponse(): Cursor {
        val matrixCursor = MatrixCursor(arrayOf(COLUMN_KEY))
        matrixCursor.addRow(arrayOf(secretKey))
        return matrixCursor
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
            "Error getting calling package name: ${e.message}".logW(LOG_TAG)
            null
        }
    }

    /**
     * 验证调用方是否可信（使用可配置的安全策略）
     */
    private fun isTrustedCaller(pkg: String): Boolean {
        val context = context ?: return false
        val config = SecurityConfigManager.getConfig()

        return try {
            // 1. 首先检查包名是否在白名单中
            if (config.isPackageWhitelistEnabled()) {
                if (!isAllowedPackage(pkg)) {
                    "Package not in whitelist: $pkg".logW(LOG_TAG)
                    return false
                }
            }

            // 2. 检查是否启用了签名指纹验证
            if (config.isFingerprintVerificationEnabled()) {
                val expectedFingerprints = config.getExpectedFingerprints(pkg)
                if (expectedFingerprints.isEmpty()) {
                    "No expected fingerprints for package: $pkg".logW(LOG_TAG)
                    return false
                }

                val actualFingerprint = getPackageSignatureFingerprint(context.packageManager, pkg)
                if (actualFingerprint == null) {
                    "Cannot get signature fingerprint for package: $pkg".logW(LOG_TAG)
                    return false
                }

                // 检查实际指纹是否匹配任何一个预期指纹
                val isMatch = expectedFingerprints.any { expected ->
                    expected.equals(actualFingerprint, ignoreCase = true)
                }

                if (isMatch) {
                    "Trusted caller verified: $pkg with fingerprint: $actualFingerprint".logD(LOG_TAG)
                } else {
                    "Signature mismatch for $pkg. Expected: $expectedFingerprints, Actual: $actualFingerprint".logW(LOG_TAG)
                }

                return isMatch
            }

            // 如果两个验证都没启用，则允许访问（不推荐在生产环境中使用）
            if (!config.isPackageWhitelistEnabled() && !config.isFingerprintVerificationEnabled()) {
                "Warning: No security verification enabled!".logW(LOG_TAG)
                return true
            }

            true

        } catch (e: PackageManager.NameNotFoundException) {
            "Package not found: $pkg".logW(LOG_TAG)
            false
        } catch (e: Exception) {
            "Error verifying caller trust: ${e.message}".logW(LOG_TAG)
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
            "Error getting signature fingerprint for $packageName: ${e.message}".logE(LOG_TAG)
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

        "=== Signature Fingerprints (Debug Mode) ===".logD(LOG_TAG)

        packagesToCheck.forEach { pkg ->
            try {
                val fingerprint = getPackageSignatureFingerprint(pm, pkg)
                "$pkg: $fingerprint".logD(LOG_TAG)
            } catch (e: Exception) {
                "$pkg: Error - ${e.message}".logE(LOG_TAG)
            }
        }

        "=== End Fingerprints ===".logD(LOG_TAG)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        val callingPackage = getCallingPackageName()
        "insert() called by: $callingPackage (not supported)".logD(LOG_TAG)
        return null
    }

    override fun getType(uri: Uri): String? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        "delete() called (not supported)".logD(LOG_TAG)
        return 0
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        "update() called (not supported)".logD(LOG_TAG)
        return 0
    }
}