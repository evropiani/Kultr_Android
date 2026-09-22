package app.kultr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Kultr") }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "kultr.open_player"
    }
}
