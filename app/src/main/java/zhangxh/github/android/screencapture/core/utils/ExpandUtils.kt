package zhangxh.github.android.screencapture.core.utils


fun Any.logger(): LoggerUtils.Companion {
    return LoggerUtils.Companion
}

fun ByteArray.insertInt32(position: Int, int: Int) {
    this[position] = (int shr 24 and 0xFF).toByte()
    this[position + 1] = (int shr 16 and 0xFF).toByte()
    this[position + 2] = (int shr 8 and 0xFF).toByte()
    this[position + 3] = (int and 0xFF).toByte()
}

fun ByteArray.insertInt16(position: Int, int: Int) {
    this[position] = (int shr 8 and 0xFF).toByte()
    this[position + 1] = (int and 0xFF).toByte()
}

fun Int.toInt16ByteArray(): ByteArray {
    val bytes = ByteArray(2)
    bytes[0] = (this shr 8 and 0xFF).toByte()
    bytes[1] = (this and 0xFF).toByte()
    return bytes
}
