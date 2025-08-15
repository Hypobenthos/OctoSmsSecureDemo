package app.octosms.smsserver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import app.octosms.commoncrypto.callback.SmsDataCallbackManager
import app.octosms.commoncrypto.config.SmsSourceConfig
import app.octosms.commoncrypto.log.logE
import app.octosms.commoncrypto.model.SmsData
import app.octosms.smsserver.push.PushServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SMSReceiver() : BroadcastReceiver() {
    private var lastTimestamp: Long = 0

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!isSourceEnabled(context)) {
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val sms = tryGetSms(context, intent)
            if (sms != null) {
                withContext(Dispatchers.IO) {
                    SmsDataCallbackManager.notifyReceived(sms, sms.sender)
                    // 调用 ServiceLocator 中的推送服务
                    PushServiceLocator.pushService.push(context, sms)
                }
            }
        }
    }

    private fun isSourceEnabled(
        context: Context
    ): Boolean {
        return SmsSourceConfig.isEnabled()
    }

    private fun tryGetSms(context: Context, intent: Intent): SmsData? {
        return try {
            val bundle = intent.extras ?: return null
            val pdus = bundle.get("pdus") as? Array<*>
            val format = bundle.getString("format")
            if (pdus == null || pdus.isEmpty()) return null

            val sb = StringBuilder()
            var sender = ""
            var timestamp = System.currentTimeMillis()
            for (pdu in pdus) {
                val bytes = pdu as? ByteArray ?: continue
                val sms = SmsMessage.createFromPdu(bytes, format)
                sender = sms.displayOriginatingAddress ?: sender
                sb.append(sms.messageBody)
                timestamp = sms.timestampMillis
            }
            val message = sb.toString()
            if (message.isBlank()) return null
            if (timestamp == lastTimestamp) return null
            lastTimestamp = timestamp
            SmsData(sender = sender, message = message, timestamp = timestamp)
        } catch (e: Exception) {
            "Error parsing SMS: ${e.message}".logE("SMSReceiver")
            null
        }
    }

    companion object {
        val smsPermission: Array<String>
            get() = arrayOf("android.permission.RECEIVE_SMS", "android.permission.READ_SMS")
    }
}
