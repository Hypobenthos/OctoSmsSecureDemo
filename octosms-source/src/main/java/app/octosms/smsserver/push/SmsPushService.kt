package app.octosms.smsserver.push

import android.content.Context
import app.octosms.commoncrypto.model.SmsData

interface SmsPushService {
    suspend fun push(context: Context, smsData: SmsData)
}