package io.legado.app.ui.book.readRecord

import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.utils.startActivityForBook
import androidx.lifecycle.lifecycleScope
import io.legado.app.data.appDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import io.legado.app.utils.ColorUtils
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.fullScreen
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor

class ReadRecordActivity : AppCompatActivity() {

    private var bgDrawable: Drawable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        setContent {
            ReadRecordContent(
                bgDrawable = bgDrawable,
                onBackClick = { finish() },
                onBookClick = { bookName, bookAuthor ->
                    lifecycleScope.launch {
                        val book = withContext(Dispatchers.IO) {
                            appDb.bookDao.findByNameAndAuthor(bookName, bookAuthor).first()
                                ?: appDb.bookDao.findByName(bookName).firstOrNull()
                        }
                        if (book == null) {
                            SearchActivity.start(this@ReadRecordActivity, bookName)
                        } else {
                            startActivityForBook(book)
                        }
                    }
                }
            )
        }
    }
    
    private fun loadBackgroundImage() {
        try {
            bgDrawable = ThemeConfig.getBgImage(this, windowManager.defaultDisplay.run {
                android.util.DisplayMetrics().apply { getMetrics(this) }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }
            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }
    
    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        setLightStatusBar(ColorUtils.isColorLight(backgroundColor))
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

@Composable
fun ReadRecordContent(
    bgDrawable: Drawable?,
    onBackClick: () -> Unit,
    onBookClick: (String, String) -> Unit
) {
    val context = LocalContext.current
    
    val primaryColor = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    
    val isLight = ColorUtils.isColorLight(bgColor)
    
    val colorScheme = remember(isLight, primaryColor, accentColor, bgColor) {
        if (isLight) {
            lightColorScheme(
                primary = Color(primaryColor),
                secondary = Color(accentColor),
                tertiary = Color(accentColor),
                background = Color(bgColor),
                surface = Color(bgColor),
                surfaceVariant = Color(bgColor),
                onPrimary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = if (isLight) Color.Black else Color.White,
                onSurface = if (isLight) Color.Black else Color.White,
                onSurfaceVariant = if (isLight) Color.Black else Color.White,
                error = Color(0xFFE53935),
                onError = Color.White
            )
        } else {
            darkColorScheme(
                primary = Color(primaryColor),
                secondary = Color(accentColor),
                tertiary = Color(accentColor),
                background = Color(bgColor),
                surface = Color(bgColor),
                surfaceVariant = Color(bgColor),
                onPrimary = if (ColorUtils.isColorLight(primaryColor)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = if (isLight) Color.Black else Color.White,
                onSurface = if (isLight) Color.Black else Color.White,
                onSurfaceVariant = if (isLight) Color.Black else Color.White,
                error = Color(0xFFFF5252),
                onError = Color.Black
            )
        }
    }
    
    MaterialTheme(colorScheme = colorScheme) {
        BoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = Color(bgColor)
        ) {
            ReadRecordScreen(
                onBackClick = onBackClick,
                onBookClick = onBookClick
            )
        }
    }
}

@Composable
fun BoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }
        
        content()
    }
}
