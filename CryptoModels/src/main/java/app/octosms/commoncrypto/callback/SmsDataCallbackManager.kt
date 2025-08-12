package app.octosms.commoncrypto.callback

import app.octosms.commoncrypto.model.SmsData

object SmsDataCallbackManager {
    @Volatile
    private var listener: SmsDataListener? = null

    fun registerListener(l: SmsDataListener) {
        listener = l
    }

    fun unregisterListener() {
        listener = null
    }

    fun notifyReceived(smsData: SmsData, senderApp: String) {
        listener?.onSmsDataReceived(smsData, senderApp)
    }
}
