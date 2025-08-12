package app.octosms.commoncrypto.key

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通过 ContentProvider 向对方 App 获取密钥
 */
object ContentKeyFetcher {
    @Volatile
    private var cachedKey: String? = null

    fun isKeyAvailable(): Boolean = !cachedKey.isNullOrBlank()

    fun getKey(): String? = cachedKey

    fun clearKey() {
        cachedKey = null
    }

    /**
     * 如果缓存中没有密钥，则尝试通过 ContentProvider 获取
     */
    suspend fun fetchKeyIfNeeded(
        context: Context,
        keyUri: android.net.Uri = KeyConstants.KEY_URI
    ): Result<String> {
        if (cachedKey != null) return Result.success(cachedKey!!)

        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(
                    keyUri,
                    arrayOf(KeyConstants.COLUMN_KEY),
                    null,
                    null,
                    null
                )
                    ?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            cachedKey =
                                cursor.getString(cursor.getColumnIndexOrThrow(KeyConstants.COLUMN_KEY))
                            return@withContext if (!cachedKey.isNullOrBlank()) {
                                Result.success(cachedKey!!)
                            } else {
                                Result.failure(IllegalStateException("Empty key from provider"))
                            }
                        }
                    }
                Result.failure(Exception("No key returned"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
