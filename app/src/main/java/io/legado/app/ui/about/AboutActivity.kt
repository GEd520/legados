package io.legado.app.ui.about

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import io.legado.app.R
import io.legado.app.ui.theme.PageTopBarContainer
import io.legado.app.ui.theme.initLegadoComposeTheme
import io.legado.app.ui.theme.pageTopBarColors
import io.legado.app.ui.theme.setLegadoContent
import io.legado.app.utils.openUrl
import io.legado.app.utils.share

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        initLegadoComposeTheme()
        super.onCreate(savedInstanceState)
        setLegadoContent(overlayAlpha = 0.06f) {
            AboutScreen(
                fragmentManager = supportFragmentManager,
                onBackClick = { finish() },
                onShareClick = {
                    share(
                        getString(R.string.app_share_description_sigma),
                        getString(R.string.app_name)
                    )
                },
                onScoringClick = {
                    openUrl("market://details?id=$packageName")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutScreen(
    fragmentManager: FragmentManager,
    onBackClick: () -> Unit,
    onShareClick: () -> Unit,
    onScoringClick: () -> Unit
) {
    val topBarColors = pageTopBarColors()
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            PageTopBarContainer(colors = topBarColors) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = topBarColors.contentColor,
                        titleContentColor = topBarColors.contentColor,
                        actionIconContentColor = topBarColors.contentColor
                    ),
                    title = { Text(text = stringResource(R.string.about)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onShareClick) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.share)
                            )
                        }
                        IconButton(onClick = onScoringClick) {
                            Icon(
                                imageVector = Icons.Outlined.StarBorder,
                                contentDescription = stringResource(R.string.scoring)
                            )
                        }
                    }
                )
            }
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AboutHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                )
                AboutPreferenceHost(
                    fragmentManager = fragmentManager,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    )
}

@Composable
private fun AboutHeader(modifier: Modifier = Modifier) {
    val summary = stringResource(R.string.about_description_sigma)
    val highlight = stringResource(R.string.legado_gzh)
    val highlightColor = MaterialTheme.colorScheme.primary
    val summaryText = remember(summary, highlight, highlightColor) {
        buildAnnotatedString {
            val start = summary.indexOf(highlight)
            if (start < 0) {
                append(summary)
            } else {
                append(summary.substring(0, start))
                withStyle(SpanStyle(color = highlightColor)) {
                    append(highlight)
                }
                append(summary.substring(start + highlight.length))
            }
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 10.dp, vertical = 10.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name_sigma),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AboutPreferenceHost(
    fragmentManager: FragmentManager,
    modifier: Modifier = Modifier
) {
    val containerId = androidx.compose.runtime.remember { View.generateViewId() }
    val fragmentTag = "aboutFragment"

    AndroidView(
        modifier = modifier,
        factory = { context ->
            FragmentContainerView(context).apply {
                id = containerId
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    )

    DisposableEffect(fragmentManager, containerId) {
        fragmentManager.beginTransaction()
            .replace(containerId, AboutFragment(), fragmentTag)
            .commitAllowingStateLoss()
        onDispose { }
    }
}
