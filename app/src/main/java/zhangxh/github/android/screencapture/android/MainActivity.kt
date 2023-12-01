package zhangxh.github.android.screencapture.android

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import zhangxh.github.android.screencapture.service.codec.ScreenSharingManager
import zhangxh.github.android.screencapture.android.ui.theme.ScreenCaptureTheme


class MainActivity : ComponentActivity() {

    private val permissionManager: PermissionManager = PermissionManager(this)
        .append(Manifest.permission.RECORD_AUDIO)

    private val sharer: ScreenSharingManager = ScreenSharingManager(this)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenCaptureTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    StartServiceButton()
                }
            }
        }
        permissionManager.request {
            sharer.init(ScreenShareService::class.java)
            Toast.makeText(this, "Permission OK", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sharer.release()
    }

    @Composable
    fun StartServiceButton() {
        val ctx = LocalContext.current
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = {
                try {
                    sharer.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(ctx, "发生错误 ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text(text = "start")
            }

            Button(onClick = {
                try {
                    sharer.stop()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(ctx, "发生错误 ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
            }) {
                Text(text = "stop")
            }
        }
    }


}