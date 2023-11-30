package zhangxh.github.android.screencapture.core.utils

import android.util.Log

class LoggerUtils {
    companion object {
        private fun getFullyTag(): String {
            val t: String
            val stackTrace = Throwable().stackTrace
            t = if (stackTrace.size > 1) {
                val target = stackTrace[stackTrace.size - 2]

                "${target.className}.${target.methodName}"
            } else {
                " ??? "
            }
            return t;
        }

        fun info(msg: String) {


            Log.i(getFullyTag(), msg)
        }

        fun warn(msg: String) {
            Log.w(getFullyTag(), msg)
        }

        fun error(msg: String?, e: Throwable) {
            Log.e(getFullyTag(), msg ?: "null", e)
        }
    }
}