package app.octosms.commoncrypto.config

object SmsSourceConfig {
    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var pushChannel: PushChannel = PushChannel.LOCAL

    fun setEnabled(value: Boolean) {
        enabled = value
    }

    fun isEnabled(): Boolean = enabled
    fun getPushChannel(): PushChannel {
        return pushChannel
    }

    fun setPushChannel(channel: PushChannel) {
        pushChannel = channel
    }


}
