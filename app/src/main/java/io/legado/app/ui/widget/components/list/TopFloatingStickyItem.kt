package io.legado.app.ui.widget.components.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

@Composable
fun <T : Any> TopFloatingStickyItem(
    item: T?,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    AnimatedVisibility(
        visible = item != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        item?.let {
            Box(modifier = modifier) {
                content(it)
            }
        }
    }
}
