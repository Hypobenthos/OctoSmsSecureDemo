package app.octosms.commoncrypto.log

fun String.logI(tag: String) {
    LogManager.get().i(tag, this)
}

fun String.logD(tag: String) {
    LogManager.get().d(tag, this)
}

fun String.logW(tag: String) {
    LogManager.get().w(tag, this)
}

fun String.logE(tag: String, throwable: Throwable? = null) {
    LogManager.get().e(tag, this, throwable)
}

