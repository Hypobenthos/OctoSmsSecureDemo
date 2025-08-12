package app.octosms.commoncrypto.crypto

import android.util.Base64
import java.security.MessageDigest

object CryptoUtils {
    fun generateChecksum(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
}