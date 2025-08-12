package app.octosms.commoncrypto.callback

import app.octosms.commoncrypto.model.SmsData

interface SmsDataListener {
    fun onSmsDataReceived(smsData: SmsData, senderApp: String)
}