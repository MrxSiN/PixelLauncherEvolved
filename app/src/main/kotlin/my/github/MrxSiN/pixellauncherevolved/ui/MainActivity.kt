package my.github.MrxSiN.pixellauncherevolved.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import my.github.MrxSiN.pixellauncherevolved.ui.screen.SettingsScreen
import my.github.MrxSiN.pixellauncherevolved.ui.theme.PixelLauncherEvolvedTheme

/** Hosts the settings screen. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            PixelLauncherEvolvedTheme {
                SettingsScreen()
            }
        }
    }
}
