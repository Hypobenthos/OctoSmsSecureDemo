package app.octosms.commoncrypto.key


/**
 * 密钥管理接口
 */
interface KeyProvider {
    /** 获取密钥，如果不存在则生成或拉取 */
    fun getKey(): String

    /** 存储密钥 */
    fun storeKey(key: String): Boolean

    /** 清除密钥缓存 */
    fun clearKey()
}
