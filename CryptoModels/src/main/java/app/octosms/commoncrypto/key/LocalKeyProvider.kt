package app.octosms.commoncrypto.key

import java.security.SecureRandom

/**
 * 本地默认密钥管理（内存缓存 + 随机生成）
 * 可替换为 Keystore 或安全文件存储
 */
class LocalKeyProvider : KeyProvider {
    @Volatile
    private var cachedKey: String? = null

    override fun getKey(): String {
        if (cachedKey == null) {
            cachedKey = generateKey()
        }
        return cachedKey!!
    }

    override fun storeKey(key: String): Boolean {
        cachedKey = key
        return true
    }

    override fun clearKey() {
        cachedKey = null
    }

    private fun generateKey(): String {
        val allowedChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..32)
            .map { allowedChars[random.nextInt(allowedChars.length)] }
            .joinToString("")
    }
}