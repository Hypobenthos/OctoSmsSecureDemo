package app.octosms.smsserver.push

import android.content.Context
import app.octosms.commoncrypto.config.PushChannel
import app.octosms.commoncrypto.config.SmsSourceConfig
import app.octosms.commoncrypto.log.logD
import app.octosms.commoncrypto.model.SmsData

object UnifiedPushService {

    suspend fun push(context: Context, sms: SmsData) {
        when (SmsSourceConfig.getPushChannel()) {
            PushChannel.LOCAL -> {
                PushServiceLocator.pushService.push(context, sms)
            }

            PushChannel.CLOUD -> {
                PushServiceLocator.cloudPushService?.push(context, sms)
                    ?: run {
                        // 云服务不可用时，降级到本机推送
                        "Cloud service unavailable, fallback to local push".logD("UnifiedPushService")
                        PushServiceLocator.pushService.push(context, sms)
                    }
            }
        }
    }
}