package app.octosms.commoncrypto.log

object LogManager {
    @Volatile
    private var logger: Logger = DefaultLogger

    fun setLogger(customLogger: Logger) {
        logger = customLogger
    }

    fun get(): Logger = logger
}