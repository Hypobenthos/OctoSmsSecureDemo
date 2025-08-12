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
import androidx.core.net.toUri
import app.octosms.commoncrypto.key.SharedKeyManager
import app.octosms.commoncrypto.log.logD
import app.octosms.commoncrypto.log.logW

class KeyExchangeProvider : ContentProvider() {
    companion object {

        private const val COLUMN_KEY = "key"
        private const val LOG_TAG = "KeyExchangeProvider"

        // 使用 lazy 延迟初始化，线程安全
        private val secretKey: String by lazy {
            SharedKeyManager.getOrGenerateKey()
        }
    }

    override fun onCreate(): Boolean = true

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
     * 验证调用方是否可信（相同签名）
     * 重构以减少代码重复和提高可读性
     */
    private fun isTrustedCaller(pkg: String): Boolean {
        val context = context ?: return false
        val pm = context.packageManager

        return try {
            val mySignatures = getPackageSignatures(pm, context.packageName)
            val otherSignatures = getPackageSignatures(pm, pkg)

            compareSignatures(mySignatures, otherSignatures)
        } catch (e: PackageManager.NameNotFoundException) {
            "Package not found: $pkg".logW(LOG_TAG)
            false
        } catch (e: Exception) {
            "Error verifying caller trust: ${e.message}".logW(LOG_TAG)
            false
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

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? {
        val callingPackage = getCallingPackageName()
        "insert() called by: $callingPackage (not supported)".logD(LOG_TAG)
        return null
    }

    override fun getType(uri: Uri): String? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        "delete() called (not supported)".logD(LOG_TAG)
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        "update() called (not supported)".logD(LOG_TAG)
        return 0
    }
}
