package app.octosms.commoncrypto.config

import app.octosms.commoncrypto.callback.SmsDataCallbackManager
import app.octosms.commoncrypto.callback.SmsDataListener
import app.octosms.commoncrypto.key.KeyProvider
import app.octosms.commoncrypto.key.SharedKeyManager
import app.octosms.commoncrypto.log.LogManager
import app.octosms.commoncrypto.log.Logger

object OctoSmsSdk {

    fun init(block: Config.() -> Unit) {
        val config = Config().apply(block)

        // 日志
        config.logger?.let { LogManager.setLogger(it) }
        // Key Provider
        config.keyProvider?.let { SharedKeyManager.setProvider(it) }
        // 短信监听
        config.smsListener?.let { SmsDataCallbackManager.registerListener(it) }
        // 安全配置
        config.securityConfig?.let { SecurityConfigManager.setConfig(it) }
    }

    class Config {
        var logger: Logger? = null
        var keyProvider: KeyProvider? = null
        var smsListener: SmsDataListener? = null
        var securityConfig: SecurityConfig? = null

        /**
         * 配置安全策略的便捷方法
         */
        fun security(block: SecurityConfigBuilder.() -> Unit) {
            securityConfig = SecurityConfigManager.builder().apply(block).build()
        }
    }

    fun shutdown() {
        SmsDataCallbackManager.unregisterListener()
        SharedKeyManager.clearKey()
        // 清理安全配置
        SecurityConfigManager.setConfig(DefaultSecurityConfig())
    }
}