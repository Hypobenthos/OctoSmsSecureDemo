package app.octosms.commoncrypto.config

/**
 * 安全配置管理器
 */
object SecurityConfigManager {
    private var config: SecurityConfig = DefaultSecurityConfig()

    /**
     * 设置安全配置
     */
    fun setConfig(securityConfig: SecurityConfig) {
        config = securityConfig
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): SecurityConfig = config

    /**
     * 创建配置构建器
     */
    fun builder(): SecurityConfigBuilder = SecurityConfigBuilder()
}