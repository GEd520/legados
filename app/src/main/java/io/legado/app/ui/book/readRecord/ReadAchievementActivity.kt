package io.legado.app.ui.book.readRecord

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import io.legado.app.ui.theme.LegadoThemeWithBackground
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.setLegadoContent

class ReadAchievementActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)

        setLegadoContent {
            ReadAchievementScreen(
                onBackClick = { finish() }
            )
        }
    }
}

@Composable
fun ReadAchievementContent(
    onBackClick: () -> Unit
) {
    LegadoThemeWithBackground(backgroundDrawable = null) {
        ReadAchievementScreen(onBackClick = onBackClick)
    }
}
