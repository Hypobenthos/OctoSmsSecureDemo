package app.octosms.smsserver.push

object PushServiceLocator {
    // 默认实现为 SecurePushService
    var pushService: SmsPushService = SecurePushService
    var cloudPushService: SmsPushService? = null
}