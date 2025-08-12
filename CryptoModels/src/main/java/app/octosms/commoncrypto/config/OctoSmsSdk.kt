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
    }

    class Config {
        var logger: Logger? = null
        var keyProvider: KeyProvider? = null
        var smsListener: SmsDataListener? = null
    }

    fun shutdown() {
        SmsDataCallbackManager.unregisterListener()
        SharedKeyManager.clearKey()
    }
}
