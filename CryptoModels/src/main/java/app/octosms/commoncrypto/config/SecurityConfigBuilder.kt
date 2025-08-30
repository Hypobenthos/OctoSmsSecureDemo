package app.octosms.commoncrypto.config

/**
 * 配置构建器
 */
/**
 * 配置构建器
 */
class SecurityConfigBuilder {
    private val config = DefaultSecurityConfig()

    fun addAllowedApp(packageName: String, vararg sha1Fingerprints: String): SecurityConfigBuilder {
        config.addAllowedPackage(packageName, *sha1Fingerprints)
        return this
    }

    fun addAllowedApp(packageName: String, sha1Fingerprints: Set<String>): SecurityConfigBuilder {
        config.addAllowedPackage(packageName, sha1Fingerprints)
        return this
    }

    fun addAllowedApp(packageName: String): SecurityConfigBuilder {
        config.addAllowedPackage(packageName)
        return this
    }

    fun enablePackageWhitelist(enabled: Boolean = true): SecurityConfigBuilder {
        config.setPackageWhitelistEnabled(enabled)
        return this
    }

    fun enableFingerprintVerification(enabled: Boolean = true): SecurityConfigBuilder {
        config.setFingerprintVerificationEnabled(enabled)
        return this
    }

    fun enableDebugMode(enabled: Boolean = true): SecurityConfigBuilder {
        config.setDebugMode(enabled)
        return this
    }

    fun build(): SecurityConfig = config
}