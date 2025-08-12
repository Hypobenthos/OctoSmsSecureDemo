package app.octosms.commoncrypto.key

/**
 * 静态共享 Key 管理器
 * 允许注入自定义 KeyProvider（方便替换）
 */
object SharedKeyManager {
    @Volatile
    private var provider: KeyProvider = LocalKeyProvider()

    fun setProvider(customProvider: KeyProvider) {
        provider = customProvider
    }

    fun getOrGenerateKey(): String = provider.getKey()

    fun storeKey(key: String): Boolean = provider.storeKey(key)

    fun clearKey() = provider.clearKey()
}