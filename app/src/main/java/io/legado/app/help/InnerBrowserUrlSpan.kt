package io.legado.app.help

import android.text.style.URLSpan
import android.view.View
import io.legado.app.utils.openInInnerBrowser

class InnerBrowserUrlSpan(url: String) : URLSpan(url) {

    override fun onClick(widget: View) {
        widget.context.openInInnerBrowser(url)
    }

}
