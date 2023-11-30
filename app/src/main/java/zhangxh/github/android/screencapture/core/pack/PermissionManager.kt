package zhangxh.github.android.screencapture.core.pack

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private val permissions = mutableListOf<String>()

    private var callback: () -> Unit = {}

    private val requestPermissionResult = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        var allSuccessful = true
        result.forEach { item ->
            if (!item.value) {
                allSuccessful = false
                Toast.makeText(activity, "权限${item.key}是必须的，请授权后再试.", Toast.LENGTH_SHORT).show()
                return@forEach
            }
        }
        if (allSuccessful) {
            callback()
        }
    }

    fun append(permission: String): PermissionManager {
        permissions.add(permission)
        return this
    }

    fun appendWithCondition(permission: String, condition: (permission: String) -> Boolean): PermissionManager {
        if (condition(permission)) {
            permissions.add(permission)
        }
        return this
    }

    fun request(callback: () -> Unit = {}) {
        this.callback = callback

        val needRequestPermissions = mutableListOf<String>()
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_DENIED) {
                needRequestPermissions.add(it)
            }
        }

        if (needRequestPermissions.size > 0) {
            requestPermissionResult.launch(needRequestPermissions.toTypedArray())
        } else {
            this.callback()
        }
    }

    fun registered(): Boolean {
        var allSuccessful = true
        permissions.forEach {
            if (ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_DENIED) {
                allSuccessful = false


                return@forEach
            }
        }
        return allSuccessful
    }

}