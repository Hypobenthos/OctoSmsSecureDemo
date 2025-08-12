package app.octosms.commoncrypto.key

import androidx.core.net.toUri

object KeyConstants {
    const val AUTHORITY = "app.octosms.smsclient.keyprovider"
    const val KEY_PATH = "key"
    const val COLUMN_KEY = "key"
    val KEY_URI = "content://$AUTHORITY/$KEY_PATH".toUri()
}
