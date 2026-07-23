package dev.basiliskus.onda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.basiliskus.onda.ui.OndaRoot
import dev.basiliskus.onda.ui.theme.OndaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OndaTheme { OndaRoot((application as OndaApp).graph) } }
    }
}
