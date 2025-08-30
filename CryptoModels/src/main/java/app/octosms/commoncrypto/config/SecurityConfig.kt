package app.octosms.commoncrypto.config

/**
 * 安全配置接口
 */
interface SecurityConfig {
    /**
     * 获取允许的应用包名列表
     */
    fun getAllowedPackages(): Set<String>

    /**
     * 获取指定包名的预期 SHA1 指纹列表（支持多个指纹）
     */
    fun getExpectedFingerprints(packageName: String): Set<String>

    /**
     * 是否启用包名白名单检查
     */
    fun isPackageWhitelistEnabled(): Boolean

    /**
     * 是否启用签名指纹验证
     */
    fun isFingerprintVerificationEnabled(): Boolean

    /**
     * 获取调试模式状态
     */
    fun isDebugMode(): Boolean
}

/**
 * 默认的安全配置实现
 */
class DefaultSecurityConfig : SecurityConfig {
    private val allowedPackages = mutableSetOf<String>()
    private val packageFingerprints = mutableMapOf<String, MutableSet<String>>() // 改为 Set 支持多个指纹
    private var packageWhitelistEnabled = true
    private var fingerprintVerificationEnabled = true
    private var debugMode = false

    override fun getAllowedPackages(): Set<String> = allowedPackages.toSet()

    override fun getExpectedFingerprints(packageName: String): Set<String> =
        packageFingerprints[packageName]?.toSet() ?: emptySet()

    override fun isPackageWhitelistEnabled(): Boolean = packageWhitelistEnabled

    override fun isFingerprintVerificationEnabled(): Boolean = fingerprintVerificationEnabled

    override fun isDebugMode(): Boolean = debugMode

    // 配置方法
    fun addAllowedPackage(packageName: String, sha1Fingerprints: Set<String> = emptySet()) {
        allowedPackages.add(packageName)
        if (sha1Fingerprints.isNotEmpty()) {
            packageFingerprints.getOrPut(packageName) { mutableSetOf() }.addAll(sha1Fingerprints)
        }
    }

    fun addAllowedPackage(packageName: String, vararg sha1Fingerprints: String) {
        addAllowedPackage(packageName, sha1Fingerprints.toSet())
    }

    fun addFingerprintToPackage(packageName: String, sha1Fingerprint: String) {
        packageFingerprints.getOrPut(packageName) { mutableSetOf() }.add(sha1Fingerprint)
    }

    fun removeAllowedPackage(packageName: String) {
        allowedPackages.remove(packageName)
        packageFingerprints.remove(packageName)
    }

    fun setPackageWhitelistEnabled(enabled: Boolean) {
        packageWhitelistEnabled = enabled
    }

    fun setFingerprintVerificationEnabled(enabled: Boolean) {
        fingerprintVerificationEnabled = enabled
    }

    fun setDebugMode(enabled: Boolean) {
        debugMode = enabled
    }

    fun clearAll() {
        allowedPackages.clear()
        packageFingerprints.clear()
    }
}