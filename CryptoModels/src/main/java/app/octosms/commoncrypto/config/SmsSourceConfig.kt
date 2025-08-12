package app.octosms.commoncrypto.config

object SmsSourceConfig {
    @Volatile private var enabled: Boolean = true

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled
}
